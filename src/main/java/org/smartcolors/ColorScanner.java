package org.smartcolors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.AbstractPeerEventListener;
import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.BloomFilter;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

/**
 * A blockchain and peer listener that keeps a set of color trackers updated with blockchain events.
 */
public class ColorScanner implements PeerFilterProvider, BlockChainListener {
	private static final Logger log = LoggerFactory.getLogger(ColorScanner.class);

	private final AbstractPeerEventListener peerEventListener;
	Set<ColorProof> proofs = Sets.newHashSet();
	// General lock.  Wallet lock is internally obtained first for any wallet related work.
	protected final ReentrantLock lock = Threading.lock("colorScanner");
	// Lock for bloom filter recalc.  General lock is obtained internally after FilterMerger obtains
	// this lock and the wallet lock (in any order).
	protected final ReentrantLock filterLock = Threading.lock("colorScannerFilter");
	@GuardedBy("lock")
	SetMultimap<Sha256Hash, SortedTransaction> mapBlockTx = TreeMultimap.create();
	@GuardedBy("lock")
	Map<Sha256Hash, Transaction> pending = Maps.newConcurrentMap();
	@GuardedBy("lock")
	private Multimap<Transaction, SettableFuture<Transaction>> unknownTransactionFutures = ArrayListMultimap.create();

	public ColorScanner() {
		peerEventListener = new AbstractPeerEventListener() {
			@Override
			public void onTransaction(Peer peer, Transaction t) {
				log.info("pending {}", t);
				pending.put(t.getHash(), t);
			}

			@Override
			public void onPeerConnected(Peer peer, int peerCount) {
				log.info("Peer connected {}", peer);
				peer.addEventListener(this);
			}
		};
	}

	public AbstractPeerEventListener getPeerEventListener() {
		return peerEventListener;
	}

	/** Add a color to the set of tracked colors */
	public void addDefinition(ColorDefinition definition) {
		lock.lock();
		try {
			proofs.add(new ColorProof(definition));
		} finally {
   			lock.unlock();
		}
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
			doRorganize(oldBlocks, newBlocks);
		} finally {
   			lock.unlock();
		}
	}

	private void doRorganize(List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) {
		log.info("reorganize {} -> {}", newBlocks.size(), oldBlocks.size());
		// Remove transactions from old blocks
		for (ColorProof proof : proofs) {
			blocks:
			for (StoredBlock block : oldBlocks) {
				for (SortedTransaction tx : mapBlockTx.get(block.getHeader().getHash())) {
					if (proof.contains(tx.tx)) {
						proof.undo(tx.tx);
						// Transactions that are topologically later are automatically removed by
						// ColorProof.undo, so we can break here.
						break blocks;
					}
				}
			}
		}

		// Add transactions from new blocks
		for (ColorProof proof : proofs) {
			for (StoredBlock block : newBlocks) {
				for (SortedTransaction tx : mapBlockTx.get(block.getHeader().getHash())) {
					if (proof.isTransactionRelevant(tx.tx)) {
						proof.add(tx.tx);
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
			if (!isRelevant(tx))
				return false;
			mapBlockTx.put(block.getHeader().getHash(), new SortedTransaction(tx, relativityOffset));
			if (blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
				for (ColorProof proof : proofs) {
					if (proof.isTransactionRelevant(tx)) {
						proof.add(tx);
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
			if (chunks.size() == 2 && chunks.get(0).opcode == ScriptOpCodes.OP_RETURN && Arrays.equals(chunks.get(1).data, ColorProof.SMART_ASSET_MARKER.getBytes())) {
				return true;
			}
		}
		log.info("not relevant");
		return false;
	}

	@Override
	public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
		Transaction tx = pending.get(txHash);
		log.info("in block {} {} {}", txHash, tx, relativityOffset);
		return (tx != null) && receive(tx, block, blockType, relativityOffset);
	}

	@Override
	public long getEarliestKeyCreationTime() {
		lock.lock();
		try {
			long creationTime = Long.MAX_VALUE;
			for (ColorProof proof : proofs) {
				creationTime = Math.min(creationTime, proof.getCreationTime());
			}
			return creationTime;
		} finally {
            lock.unlock();
		}
	}

	@Override
	public int getBloomFilterElementCount() {
		int count = 0;
		lock.lock();
		try {
			for (ColorProof proof : proofs) {
				count += proof.getBloomFilterElementCount();
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
			for (ColorProof proof : proofs) {
				proof.updateBloomFilter(filter);
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

	@Override
	public Lock getLock() {
		return filterLock;
	}

	/**
	 * Get the net movement of assets caused by the transaction.
	 *
	 * <p>If we notice an output that is marked as carrying color, but we don't know what asset
	 * it is, it will be marked as {@link org.smartcolors.ColorDefinition#UNKNOWN}</p>
	 */
	public Map<ColorDefinition, Long> getNetAssetChange(Transaction tx, Wallet wallet) {
		wallet.getLock().lock();
		try {
			Map<ColorDefinition, Long> res = Maps.newHashMap();
			applyNetAssetChange(tx, wallet, res);
			return res;
		} finally {
   			wallet.getLock().unlock();
		}
	}

	private void applyNetAssetChange(Transaction tx, Wallet wallet, Map<ColorDefinition, Long> res) {
		lock.lock();
		try {
			outs: for (TransactionOutput out : getColoredOutputs(tx)) {
				if (out.isMine(wallet)) {
					if (applyOutputValue(out, res)) continue outs;
				}
			}
			inps: for (TransactionInput inp: tx.getInputs()) {
				if (isInputMine(inp, wallet)) {
					for (ColorProof proof : proofs) {
						Long value = proof.getOutputs().get(inp.getOutpoint());
						if (value != null) {
							Long existing = res.get(proof.getDefinition());
							if (existing != null)
								value = existing - value;
							res.put(proof.getDefinition(), value);
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
		for (ColorProof proof: proofs) {
			Long value = proof.getOutputs().get(out.getOutPointFor());
			if (value != null) {
				Long existing = res.get(proof.getDefinition());
				if (existing != null)
					value = existing + value;
				res.put(proof.getDefinition(), value);
				return true;
			}
		}
		// Unknown asset on this output
		Long value = SmartColors.removeMsbdropValuePadding(out.getValue().getValue());
		Long existing = res.get(ColorDefinition.UNKNOWN);
		if (existing != null)
			value = value + existing;
		res.put(ColorDefinition.UNKNOWN, value);
		return false;
	}

	public Map<ColorDefinition, Long> getBalances(Wallet wallet, ColorKeyChain colorKeyChain) {
		Map<ColorDefinition, Long> res = Maps.newHashMap();
		res.put(ColorDefinition.BITCOIN, 0L);
		wallet.getLock().lock();
		lock.lock();
		try {
			LinkedList<TransactionOutput> all = wallet.calculateAllSpendCandidates(false);
			for (TransactionOutput output: all) {
				if (colorKeyChain.isOutputToMe(output))
					applyOutputValue(output, res);
				else
					res.put(ColorDefinition.BITCOIN, res.get(ColorDefinition.BITCOIN) + output.getValue().getValue());
			}
		} finally {
   			lock.unlock();
			wallet.getLock().unlock();
		}
		return res;
	}

	/**
	 * Get a future that will be ready when we make progress finding out the asset types that this
	 * transaction outputs, or throw {@link org.smartcolors.ColorScanner.ScanningException}
	 * if we were unable to ascertain some of the outputs.
	 *
	 * <p>The caller may have to run this again if we find one asset, but there are other unknown outputs</p>
 	 */
	public ListenableFuture<Transaction> getTransactionWithKnownAssets(Transaction tx, Wallet wallet) {
		wallet.getLock().lock();
		lock.lock();
		try {
			SettableFuture<Transaction> future = SettableFuture.create();
			if (getNetAssetChange(tx, wallet).containsKey(ColorDefinition.UNKNOWN)) {
				// FIXME need to fail here right away if we are past the block where this tx appears and we are bloom filtering
				unknownTransactionFutures.put(tx, future);
			} else {
				future.set(tx);
			}
			return future;
		} finally {
            lock.unlock();
			wallet.getLock().unlock();
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

	private List<TransactionOutput> getColoredOutputs(Transaction tx) {
		long mask = ~0;
		for (TransactionInput input: tx.getInputs()) {
			mask = mask & input.getSequenceNumber();
		}

		List<TransactionOutput> outputs = Lists.newArrayList();
		for (TransactionOutput output: tx.getOutputs()) {
			if ((mask & 1) == 1) {
				outputs.add(output);
			}
			mask = mask >> 1;
		}

		return outputs;
	}

	void setMapBlockTx(SetMultimap<Sha256Hash, SortedTransaction> mapBlockTx) {
		this.mapBlockTx = mapBlockTx;
	}

	SetMultimap<Sha256Hash, SortedTransaction> getMapBlockTx() {
		return mapBlockTx;
	}

	public Set<ColorProof> getColorProofs() {
		return proofs;
	}

	Map<Sha256Hash,Transaction> getPending() {
		return pending;
	}

	void setPending(Map<Sha256Hash, Transaction> pending) {
		this.pending = pending;
	}

	public static class ScanningException extends RuntimeException {
		public ScanningException(String reason) {
			super(reason);
		}
	}

	public Set<ColorDefinition> getColorDefinitions() {
		lock.lock();
		try {
			Set<ColorDefinition> colors = Sets.newHashSet();
			colors.add(ColorDefinition.BITCOIN);
			for (ColorProof proof: proofs) {
				colors.add(proof.getDefinition());
			}
			colors.add(ColorDefinition.UNKNOWN);
			return colors;
		} finally {
   			lock.unlock();
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("[ColorScanner\n");
		for (ColorProof proof: proofs) {
			builder.append(proof.toString());
		}
		builder.append("]");
		return builder.toString();
	}

	public ColorDefinition getColorDefinitionByHash(Sha256Hash hash) {
		for (ColorDefinition def: getColorDefinitions()) {
			if (def.getHash().equals(hash))
				return def;
		}
		return null;
	}

	public ColorProof getColorProofByHash(Sha256Hash hash) {
		for (ColorProof proof: proofs) {
			if (proof.getDefinition().getHash().equals(hash))
				return proof;
		}
		return null;
	}

	/** Reset all state.  Used for blockchain rescan. */
	public void reset() {
		lock.lock();
		try {
			for (ColorProof proof: proofs) {
				proof.reset();
			}
			unknownTransactionFutures.clear();
			mapBlockTx.clear();
			pending.clear();
		} finally {
   			lock.unlock();
		}
	}
}
