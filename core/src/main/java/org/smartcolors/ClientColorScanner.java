package org.smartcolors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Objects;
import com.google.common.collect.Queues;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.SettableFuture;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bitcoinj.core.AbstractWalletEventListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.ColorProof;
import org.smartcolors.core.SmartColors;
import org.smartcolors.marshal.BytesDeserializer;
import org.smartcolors.marshal.SerializationException;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public class ClientColorScanner extends AbstractColorScanner {
	private static final Logger log = LoggerFactory.getLogger(ClientColorScanner.class);
	private static final Transaction SENTINEL =
			new Transaction(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
	public static final int NETWORK_TIMEOUT = 10000;

	private final Iterable<ClientColorTrack> clientTracks;
	Fetcher fetcher;
	final BlockingDeque<Transaction> lookups;
	MyService service;
	private ColorKeyChain colorKeyChain;

	public ClientColorScanner(NetworkParameters params, URI baseUri) {
		super(params);
		lookups = Queues.newLinkedBlockingDeque();
		fetcher = new Fetcher(baseUri, params);
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

	public void setColorKeyChain(ColorKeyChain colorKeyChain) {
		this.colorKeyChain = colorKeyChain;
	}

	public void start() {
		checkState(service == null, "already started service");
		checkNotNull(colorKeyChain);
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
			// FIXME just colorKeyChain outputs
			if (!walletOutputs.isEmpty()) {
				pending.put(tx.getHash(), tx);
				lookups.add(tx);
			}
		} finally {
   			lock.unlock();
			wallet.endBloomFilterCalculation();
		}
	}

	class MyService extends AbstractExecutionThreadService {
		@Override
		protected void run() throws InterruptedException {
			while (isRunning()) {
				Transaction tx = lookups.take();
				if (tx == SENTINEL)
					break;
				// Put it back in case we fail
				lookups.addFirst(tx);
				for (TransactionOutput output : tx.getOutputs()) {
					try {
						if (colorKeyChain.isOutputToMe(output))
							doOutPoint(output.getOutPointFor());
					} catch (SerializationException e) {
						log.error("serialization problem", e);
					} catch (TemporaryFailureException e) {
						log.warn("tempfail " + output);
					}
				}
				doTransaction(tx);
			}

			// Remove any sentinel at end
			while (lookups.peekLast() == SENTINEL) {
				lookups.removeLast();
			}
		}

		private void doOutPoint(TransactionOutPoint outPoint) throws SerializationException, TemporaryFailureException {
			ColorProof proof = fetcher.fetch(outPoint);
			if (proof == null)
				return;
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

		private void doTransaction(Transaction tx) {
			lock.lock();
			try {
				pending.remove(tx.getHash());
				checkState(tx == lookups.removeFirst());
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

	private static class TemporaryFailureException extends Exception {
	}

	@JsonDeserialize(keyUsing = HexKeyDeserializer.class)
	public static class ProofMap extends HashMap<HashCode, byte[]> {
	}

	public static class OutPointResponse {
		public String status;
		public String error;
		public String details;
		@JsonDeserialize(keyUsing = HexKeyDeserializer.class)
		public Map<HashCode, ProofMap> colordefs;

		@Override
		public String toString() {
			return Objects.toStringHelper(this)
					.add("status", status)
					.add("error", error)
					.add("details", details)
					.toString();
		}
	}

	public static class Fetcher {
		private final URI base;
		private final NetworkParameters params;
		CloseableHttpClient httpclient;
		ObjectMapper mapper = new ObjectMapper();

		public Fetcher(URI base, NetworkParameters params) {
			this.base = base;
			this.params = params;
			initClient();
		}

		private void initClient() {
			RequestConfig config = RequestConfig.custom()
					.setConnectionRequestTimeout(NETWORK_TIMEOUT)
					.setConnectTimeout(NETWORK_TIMEOUT)
					.setSocketTimeout(NETWORK_TIMEOUT)
					.build();
			httpclient =  HttpClients.custom()
					.setUserAgent("SmartColors-java-" + SmartColors.getVersion())
					.setDefaultRequestConfig(config)
					.build();
		}

		public ColorProof fetch(TransactionOutPoint point) throws SerializationException, TemporaryFailureException {
			String relative = "outpoint/" + point.getHash() + "/" + point.getIndex();
			log.info("fetching " + relative);
			HttpGet get = new HttpGet(base.resolve(relative));

			CloseableHttpResponse response = null;
			try {
				response = httpclient.execute(get);
				if (response.getStatusLine().getStatusCode() != 200) {
					log.warn("got status " + response.getStatusLine());
					throw new TemporaryFailureException();
				}
				OutPointResponse res = mapper.readValue(response.getEntity().getContent(), OutPointResponse.class);
				if ("NOT_COLORED".equals(res.status))
					return null;
				if ("NOT_FOUND".equals(res.status))
					throw new TemporaryFailureException();
				if (!"OK".equals(res.status)) {
					log.warn("got unknown result " + res);
					throw new TemporaryFailureException();
				}
				for (ProofMap map : res.colordefs.values()) {
					for (byte[] bytes : map.values()) {
						return ColorProof.deserialize(params, new BytesDeserializer(bytes));
					}
				}
			} catch (IOException e) {
				// temporary failure
				log.warn("got IOException " + e.getMessage());
				return null;
			} finally {
				if (response != null) {
					try {
						response.close();
					} catch (IOException e) {
						log.warn("got IOException while closing " + e.getMessage());
						// ignore
					}
				}
			}
			return null;
		}

		public void stop() {
			try {
				httpclient.close();
			} catch (IOException e) {
				log.error("while stopping fetcher", e);
			}
			initClient();
		}
	}

	@Override
	public void doReset() {
		checkState(service == null, "service still running");
		lookups.clear();
		fetcher.stop();
	}
}
