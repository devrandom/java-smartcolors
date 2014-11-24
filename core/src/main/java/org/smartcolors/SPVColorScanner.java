package org.smartcolors;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.AbstractPeerEventListener;
import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerFilterProvider;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.SmartColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A blockchain and peer listener that keeps a set of color trackers updated with blockchain events.
 *
 * <p>You must call {@link #addAllPending(java.util.Collection)} after deserializing the wallet
 * so that we pick up any pending transactions that were saved with the wallet but we didn't get to save.
 * The peer will not let us know about it again, and we will be unable to find it when its hash
 * gets into a block.
 * </p>
 */
public class SPVColorScanner extends AbstractColorScanner implements PeerFilterProvider, BlockChainListener {
	private static final Logger log = LoggerFactory.getLogger(SPVColorScanner.class);

	private final AbstractPeerEventListener peerEventListener;
	private final Iterable<SPVColorTrack> spvTracks;

	// Lock for bloom filter recalc.  General lock is obtained internally after FilterMerger obtains
	// this lock and the wallet lock (in any order).
	protected final ReentrantLock filterLock = Threading.lock("colorScannerFilter");
	@GuardedBy("lock")
	SetMultimap<Sha256Hash, SortedTransaction> mapBlockTx = TreeMultimap.create();
	@GuardedBy("lock")
	Map<Sha256Hash, Transaction> pending = Maps.newConcurrentMap();
	@GuardedBy("lock")
	private Multimap<Transaction, SettableFuture<Transaction>> unknownTransactionFutures = ArrayListMultimap.create();

	public SPVColorScanner(NetworkParameters params) {
		super(params);
		peerEventListener = new AbstractPeerEventListener() {
			@Override
			public void onTransaction(Peer peer, Transaction t) {
				addPending(t);
			}

			@Override
			public void onPeerConnected(Peer peer, int peerCount) {
				log.info("Peer connected {}", peer);
				peer.addEventListener(this);
			}
		};
		spvTracks = new Iterable<SPVColorTrack>() {
			@Override
			public Iterator<SPVColorTrack> iterator() {
				final Iterator iter = tracks.iterator();
				return new Iterator<SPVColorTrack>() {
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}

					@Override
					public SPVColorTrack next() {
						return (SPVColorTrack) iter.next();
					}

					@Override
					public void remove() {
						iter.remove();
					}
				};
			}
		};
	}

	/** Add a pending transaction from a peer or outgoing from us */
	public void addPending(Transaction t) {
		log.info("pending {}", t);
		pending.put(t.getHash(), t);
	}

	public AbstractPeerEventListener getPeerEventListener() {
		return peerEventListener;
	}

	@Override
	protected SPVColorTrack makeTrack(ColorDefinition definition) {
		return new SPVColorTrack(definition);
	}

	@Override
	public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
		lock.lock();
		ArrayList<SettableFuture<Transaction>> futures;
		try {
			futures = Lists.newArrayList(unknownTransactionFutures.values());
			unknownTransactionFutures.clear();
		} finally {
   			lock.unlock();
		}
		// Assume that any pending unknowns will not become known and therefore should fail
		for (SettableFuture<Transaction> future: futures) {
			future.setException(new ScanningException("could not find asset type"));
		}
	}

	@Override
	public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
		lock.lock();
		try {
			doReorganize(oldBlocks, newBlocks);
		} finally {
   			lock.unlock();
		}
	}

	private void doReorganize(List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) {
		log.info("reorganize {} -> {}", newBlocks.size(), oldBlocks.size());
		// Remove transactions from old blocks
		for (SPVColorTrack track : spvTracks) {
			blocks:
			for (StoredBlock block : oldBlocks) {
				for (SortedTransaction tx : mapBlockTx.get(block.getHeader().getHash())) {
					if (track.contains(tx.tx)) {
						track.undo(tx.tx);
						// Transactions that are topologically later are automatically removed by
						// ColorTrack.undo, so we can break here.
						break blocks;
					}
				}
			}
		}

		// Add transactions from new blocks
		for (SPVColorTrack track : spvTracks) {
			for (StoredBlock block : newBlocks) {
				for (SortedTransaction tx : mapBlockTx.get(block.getHeader().getHash())) {
					if (track.isTransactionRelevant(tx.tx)) {
						track.add(tx.tx);
					}
				}
			}
		}
	}

	@Override
	public boolean isTransactionRelevant(Transaction tx) throws ScriptException {
		log.info("isRelevant {}", tx.getHash());
		return isRelevant(tx);
	}

	@Override
	public void receiveFromBlock(Transaction tx, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
		receive(tx, block, blockType, relativityOffset);
	}

	private boolean receive(Transaction tx, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) {
		lock.lock();
		Collection<SettableFuture<Transaction>> futures = null;
		try {
			log.info("receive {} {}", tx, relativityOffset);
			mapBlockTx.put(block.getHeader().getHash(), new SortedTransaction(tx, relativityOffset));
			if (blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
				for (SPVColorTrack track : spvTracks) {
					if (track.isTransactionRelevant(tx)) {
						track.add(tx);
					}
				}
				futures = unknownTransactionFutures.removeAll(tx);
			}
		} finally {
			lock.unlock();
		}

		if (futures != null) {
			for (SettableFuture<Transaction> future : futures) {
				future.set(tx);
			}
		}
		return true;
	}

	private boolean isRelevant(Transaction tx) {
		for (TransactionOutput output: tx.getOutputs()) {
			Script script = output.getScriptPubKey();
			List<ScriptChunk> chunks = script.getChunks();
			if (chunks.size() == 2 && chunks.get(0).opcode == ScriptOpCodes.OP_RETURN && Arrays.equals(chunks.get(1).data, SPVColorTrack.SMART_ASSET_MARKER.getBytes())) {
				return true;
			}
		}

		// Try some more while our genesis points don't have OP_RETURN
		for (SPVColorTrack track : spvTracks) {
			if (track.isTransactionRelevant(tx)) {
				return true;
			}
		}

		log.info("not relevant");
		return false;
	}

	@Override
	public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
		Transaction tx = pending.get(txHash);
		if (tx == null) {
			log.error("in block with no pending tx {} {}", txHash, tx, relativityOffset);
			return false;
		} else {
			log.info("in block {} {} {}", txHash, tx, relativityOffset);
			return receive(tx, block, blockType, relativityOffset);
		}
	}

	@Override
	public long getEarliestKeyCreationTime() {
		lock.lock();
		try {
			long creationTime = Long.MAX_VALUE;
			for (SPVColorTrack track : spvTracks) {
				creationTime = Math.min(creationTime, track.getCreationTime() + SmartColors.EARLIEST_FUDGE);
			}
			return creationTime;
		} finally {
            lock.unlock();
		}
	}

	@Override
	public void beginBloomFilterCalculation() {
		filterLock.lock();
	}

	@Override
	public void endBloomFilterCalculation() {
		filterLock.unlock();
	}

	@Override
	public int getBloomFilterElementCount() {
		int count = 0;
		lock.lock();
		try {
			for (SPVColorTrack track : spvTracks) {
				count += track.getBloomFilterElementCount();
			}
		} finally {
            lock.unlock();
		}
		return count;
	}

	@Override
	public BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak) {
		BloomFilter filter = new BloomFilter(size, falsePositiveRate, nTweak);
		lock.lock();
		try {
			for (SPVColorTrack track : spvTracks) {
				track.updateBloomFilter(filter);
			}
		} finally {
            lock.unlock();
		}
		return filter;
	}

	@Override
	public boolean isRequiringUpdateAllBloomFilter() {
		return true;
	}

	/**
	 * Get the net movement of assets caused by the transaction.
	 *
	 * <p>If we notice an output that is marked as carrying color, but we don't know what asset
	 * it is, it will be marked as UNKNOWN</p>
	 */
	@Override
	public Map<ColorDefinition, Long> getNetAssetChange(Transaction tx, Wallet wallet, ColorKeyChain chain) {
		wallet.beginBloomFilterCalculation();
		try {
			Map<ColorDefinition, Long> res = Maps.newHashMap();
			applyNetAssetChange(tx, wallet, chain, res);
			return res;
		} finally {
   			wallet.endBloomFilterCalculation();
		}
	}

	private void applyNetAssetChange(Transaction tx, Wallet wallet, ColorKeyChain chain, Map<ColorDefinition, Long> res) {
		lock.lock();
		try {
			for (TransactionOutput out : tx.getOutputs()) {
				if (chain.isOutputToMe(out)) {
					applyOutputValue(out, res);
				}
			}
			inps: for (TransactionInput inp: tx.getInputs()) {
				if (isInputMine(inp, wallet)) {
					for (SPVColorTrack track : spvTracks) {
						Long value = track.getOutputs().get(inp.getOutpoint());
						if (value != null) {
							Long existing = res.get(track.getDefinition());
							if (existing == null)
								existing = 0L;
							res.put(track.getDefinition(), existing - value);
							continue inps;
						}
					}
				}
			}
		} finally {
   			lock.unlock();
		}
	}

	private boolean applyOutputValue(TransactionOutput out, Map<ColorDefinition, Long> res) {
		for (SPVColorTrack track: spvTracks) {
			Long value = track.getOutputs().get(out.getOutPointFor());
			if (value == null) {
				// We don't know about this output yet, try applying the color kernel to figure
				// it out from the inputs.  This is likely an unconfirmed transaction.
				Long[] colorOuts = track.applyKernel(out.getParentTransaction());
				value = colorOuts[out.getIndex()];
			}
			if (value != null) {
				Long existing = res.get(track.getDefinition());
				if (existing != null)
					value = existing + value;
				res.put(track.getDefinition(), value);
				return true;
			}
		}
		// Unknown asset on this output
		Long value = SmartColors.removeMsbdropValuePadding(out.getValue().getValue());
		Long existing = res.get(unknownDefinition);
		if (existing != null)
			value = value + existing;
		res.put(unknownDefinition, value);
		return false;
	}

	@Override
	public Map<ColorDefinition, Long> getBalances(Wallet wallet, ColorKeyChain colorKeyChain) {
		Map<ColorDefinition, Long> res = Maps.newHashMap();
		res.put(bitcoinDefinition, 0L);
		wallet.beginBloomFilterCalculation();
		lock.lock();
		try {
			LinkedList<TransactionOutput> all = wallet.calculateAllSpendCandidates(false);
			for (TransactionOutput output: all) {
				if (colorKeyChain.isOutputToMe(output))
					applyOutputValue(output, res);
				else
					res.put(bitcoinDefinition, res.get(bitcoinDefinition) + output.getValue().getValue());
			}
		} finally {
   			lock.unlock();
			wallet.endBloomFilterCalculation();
		}
		return res;
	}

	/**
	 * Get a future that will be ready when we make progress finding out the asset types that this
	 * transaction outputs, or throw {@link SPVColorScanner.ScanningException}
	 * if we were unable to ascertain some of the outputs.
	 *
	 * <p>The caller may have to run this again if we find one asset, but there are other unknownDefinition outputs</p>
 	 */
	@Override
	public ListenableFuture<Transaction> getTransactionWithKnownAssets(Transaction tx, Wallet wallet, ColorKeyChain chain) {
		wallet.beginBloomFilterCalculation();
		lock.lock();
		try {
			SettableFuture<Transaction> future = SettableFuture.create();
			if (getNetAssetChange(tx, wallet, chain).containsKey(unknownDefinition)) {
				// FIXME need to fail here right away if we are past the block where this tx appears and we are bloom filtering
				unknownTransactionFutures.put(tx, future);
			} else {
				future.set(tx);
			}
			return future;
		} finally {
            lock.unlock();
			wallet.endBloomFilterCalculation();
		}
	}

	private boolean isInputMine(TransactionInput input, Wallet wallet) {
		TransactionOutPoint outpoint = input.getOutpoint();
		TransactionOutput connected = getConnected(outpoint, wallet.getTransactionPool(WalletTransaction.Pool.UNSPENT));
		if (connected == null)
			connected = getConnected(outpoint, wallet.getTransactionPool(WalletTransaction.Pool.SPENT));
		if (connected == null)
			connected = getConnected(outpoint, wallet.getTransactionPool(WalletTransaction.Pool.PENDING));
		if (connected == null)
			return false;
		// The connected output may be the change to the sender of a previous input sent to this wallet. In this
		// case we ignore it.
		return connected.isMine(wallet);
	}

	private TransactionOutput getConnected(TransactionOutPoint outpoint, Map<Sha256Hash, Transaction> transactions) {
		Transaction tx = transactions.get(outpoint.getHash());
		if (tx == null)
			return null;
		return tx.getOutputs().get((int) outpoint.getIndex());
	}

	void setMapBlockTx(SetMultimap<Sha256Hash, SortedTransaction> mapBlockTx) {
		this.mapBlockTx = mapBlockTx;
	}

	SetMultimap<Sha256Hash, SortedTransaction> getMapBlockTx() {
		return mapBlockTx;
	}

	public Set<? extends ColorTrack> getColorTracks() {
		return tracks;
	}

	Map<Sha256Hash,Transaction> getPending() {
		return pending;
	}

	/** Call this after deserializing the wallet with any wallet pending transactions */
	public void addAllPending(Collection<Transaction> txs) {
		for (Transaction tx : txs) {
			pending.put(tx.getHash(), tx);
		}
	}

	void setPending(Map<Sha256Hash, Transaction> pending) {
		this.pending = pending;
	}

	@Override
	public Set<ColorDefinition> getDefinitions() {
		lock.lock();
		try {
			Set<ColorDefinition> colors = Sets.newHashSet();
			colors.add(bitcoinDefinition);
			for (ColorTrack track: tracks) {
				colors.add(track.getDefinition());
			}
			colors.add(unknownDefinition);
			return colors;
		} finally {
   			lock.unlock();
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("[ColorScanner\n");
		Ordering<ColorTrack> ordering = Ordering.natural().onResultOf(new Function<ColorTrack, Comparable>() {
			@Nullable
			@Override
			public Comparable apply(@Nullable ColorTrack input) {
				return input.getDefinition().getHash().toString();
			}
		});
		for (ColorTrack track: ordering.immutableSortedCopy(tracks)) {
			builder.append(track.toString());
		}
		builder.append("\n]");
		return builder.toString();
	}

	/** Reset all state.  Used for blockchain rescan. */
	@Override
	public void reset() {
		lock.lock();
		try {
			for (ColorTrack track: tracks) {
				track.reset();
			}
			unknownTransactionFutures.clear();
			mapBlockTx.clear();
			pending.clear();
		} finally {
   			lock.unlock();
		}
	}

}
