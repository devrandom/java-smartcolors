package org.smartcolors;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.SettableFuture;

import org.bitcoinj.core.AbstractWalletEventListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.Wallet;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.ColorProof;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingDeque;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public class ClientColorScanner extends AbstractColorScanner {
	private static final TransactionOutPoint SENTINEL =
			new TransactionOutPoint(NetworkParameters.fromID(NetworkParameters.ID_MAINNET), Long.MAX_VALUE, new Sha256Hash(new byte[32]));

	private final Iterable<ClientColorTrack> clientTracks;
	Fetcher fetcher;
	final BlockingDeque<TransactionOutPoint> lookups;
	MyService service;

	public ClientColorScanner(NetworkParameters params) {
		super(params);
		lookups = Queues.newLinkedBlockingDeque();
		fetcher = new Fetcher();
		clientTracks = new Iterable<ClientColorTrack>() {
			@Override
			public Iterator<ClientColorTrack> iterator() {
				final Iterator iter = tracks.iterator();
				return new Iterator<ClientColorTrack>() {
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}

					@Override
					public ClientColorTrack next() {
						return (ClientColorTrack) iter.next();
					}

					@Override
					public void remove() {
						iter.remove();
					}
				};
			}
		};
	}

	public void setFetcher(Fetcher fetcher) {
		this.fetcher = fetcher;
	}

	public void start() {
		checkState(service == null, "already started service");
		service = new MyService();
		service.startAsync();
	}

	public void stop() {
		checkNotNull(service);
		service.stopAsync();
		service.awaitTerminated();
		service = null;
	}

	@Override
	protected ColorTrack makeTrack(ColorDefinition definition) {
		return new ClientColorTrack(definition);
	}

	public void listenToWallet(Wallet wallet) {
		wallet.addEventListener(new AbstractWalletEventListener() {
			@Override
			public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
				super.onCoinsReceived(wallet, tx, prevBalance, newBalance);
				onReceive(wallet, tx);
			}

			@Override
			public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
				super.onTransactionConfidenceChanged(wallet, tx);
			}
		});
	}

	public void addAllPending(Wallet wallet, Collection<Transaction> txs) {
		super.addAllPending(wallet, txs);
		for (Transaction tx : txs) {
			onReceive(wallet, tx);
		}
	}

	void onReceive(Wallet wallet, Transaction tx) {
		wallet.beginBloomFilterCalculation();
		lock.lock();
		try {
			List<TransactionOutput> walletOutputs = tx.getWalletOutputs(wallet);
			if (!walletOutputs.isEmpty()) {
				pending.put(tx.getHash(), tx);
			}
			for (TransactionOutput output : walletOutputs) {
				lookups.add(output.getOutPointFor());
			}
			if (!walletOutputs.isEmpty()) {
				// Tell service to remove tx from pending
				lookups.add(new TransactionOutPoint(params, Long.MAX_VALUE, tx.getHash()));
			}
		} finally {
   			lock.unlock();
			wallet.endBloomFilterCalculation();
		}
	}

	class MyService extends AbstractExecutionThreadService {
		@Override
		protected void run() throws Exception {
			while (isRunning()) {
				TransactionOutPoint outPoint = lookups.take();
				if (outPoint == SENTINEL)
					break;
				// Check if this is a dummy outpoint that tells us this tx is all fetched
				if (outPoint.getIndex() == Long.MAX_VALUE) {
					doTransaction(outPoint);
					continue;
				}
				// Put it back in case we fail
				lookups.addFirst(outPoint);
				doOutPoint(outPoint);
			}

			// Remove any sentinel at end
			while (lookups.peekLast() == SENTINEL) {
				lookups.removeLast();
			}
		}

		private void doOutPoint(TransactionOutPoint outPoint) throws ColorProof.ValidationException {
			ColorProof proof = fetcher.fetch(outPoint);
			if (proof != null) {
				TransactionOutPoint first = lookups.removeFirst();
				checkState(first == outPoint);
				boolean found = false;
				lock.lock();
				try {
					for (ClientColorTrack track : clientTracks) {
						if (track.definition.equals(proof.getDefinition())) {
							track.add(proof);
							found = true;
						}
					}
					if (!found) {
						// TODO handle new asset type
					}
				} finally {
					lock.unlock();
				}
			}
		}

		private void doTransaction(TransactionOutPoint outPoint) {
			lock.lock();
			try {
				Transaction tx = pending.remove(outPoint.getHash());
				Collection<SettableFuture<Transaction>> futures = unknownTransactionFutures.removeAll(tx);
				if (futures != null) {
					for (SettableFuture<Transaction> future : futures) {
						future.set(tx);
					}
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		protected void triggerShutdown() {
			lookups.add(SENTINEL);
		}
	}

	public static class Fetcher {
		public ColorProof fetch(TransactionOutPoint point) {
			Utils.sleep(100);
			return null;
		}
	}

	@Override
	public void doReset() {
		checkState(service == null, "service still running");
		lookups.clear();
	}
}
