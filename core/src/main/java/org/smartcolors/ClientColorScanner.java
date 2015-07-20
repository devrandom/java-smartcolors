package org.smartcolors;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.ColorProof;
import org.smartcolors.core.SmartColors;
import org.smartcolors.marshal.BytesDeserializer;
import org.smartcolors.marshal.SerializationException;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public class ClientColorScanner extends AbstractColorScanner<ClientColorTrack> {
	private static final Logger log = LoggerFactory.getLogger(ClientColorScanner.class);
	public static final int NETWORK_TIMEOUT = 10000;

	Fetcher fetcher;
	ScheduledExecutorService fetchService;
	private SmartWallet wallet;


	public ClientColorScanner(NetworkParameters params) {
		this(params, getDefaultUri(params));
	}

	private static URI getDefaultUri(NetworkParameters params) {
		try {
			return new URI("http://localhost:8888/");
		} catch (URISyntaxException e) {
			throw Throwables.propagate(e);
		}
	}

	public ClientColorScanner(NetworkParameters params, URI baseUri) {
		super(params);
		fetcher = new Fetcher(baseUri, params);
	}

	public void setFetchService(ScheduledExecutorService fetchService) {
		this.fetchService = fetchService;
	}

	void setFetcher(Fetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public void stop() {
		if (fetchService == null) {
			log.warn("already stopped");
			return;
		}
		fetcher.stop();
		fetchService.shutdownNow();
		try {
			fetchService.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Throwables.propagate(e);
		}
		fetchService = null;
	}

	@Override
	public void start(Wallet wallet) {
		checkState(fetchService == null);
		checkState(colorKeyChain != null);
		this.wallet = (SmartWallet)wallet;

		// Intern the transactions so we get the right confidence level
		for (Map.Entry<Sha256Hash, Transaction> entry : pending.entrySet()) {
			pending.put(entry.getKey(), wallet.getTransaction(entry.getValue().getHash()));
		}
		fetchService = SmartColors.makeSerializationService("Fetcher thread");
		for (Transaction tx : pending.values()) {
			// Average 1 per second
			long millis = (long)(1000 * pending.size() * Math.random());
			fetchService.schedule(new Lookup(tx), millis, TimeUnit.MILLISECONDS);
		}
		listenToWallet(wallet);
		addAllPending(wallet, wallet.getPendingTransactions());
	}

	public boolean isStarted() {
		return fetchService != null;
	}

	@Override
	protected ClientColorTrack makeTrack(ColorDefinition definition) {
		return new ClientColorTrack(definition);
	}

	private void listenToWallet(Wallet wallet) {
		wallet.addEventListener(new AbstractWalletEventListener() {
			@Override
			public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
				super.onCoinsReceived(wallet, tx, prevBalance, newBalance);
				onTransaction(wallet, tx);
			}

			@Override
			public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
				// FIXME change bitcoinj so that we get this also when diff = 0
				super.onCoinsSent(wallet, tx, prevBalance, newBalance);
				onTransaction(wallet, tx);
			}

			@Override
			public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
				super.onTransactionConfidenceChanged(wallet, tx);
			}
		}, Threading.SAME_THREAD);
	}

	@Override
	protected void addAllPending(Wallet wallet, Collection<Transaction> txs) {
		for (Transaction tx : txs) {
			onTransaction(wallet, tx);
		}
	}

	void onTransaction(Wallet _wallet, Transaction tx) {
        SmartWallet wallet = (SmartWallet)_wallet;
		checkNotNull(colorKeyChain);
		wallet.lock();
		lock.lock();
		try {
			List<TransactionOutput> walletOutputs = tx.getWalletOutputs(wallet);
			boolean needsLookup = false;
			for (TransactionOutput output : walletOutputs) {
				if (colorKeyChain.isOutputToMe(output) && !contains(output.getOutPointFor())) {
					if (!tryLocalLookup(tx, false)) {
						needsLookup = true;
						break;
					}
				}
			}
			if (needsLookup) {
				pending.put(tx.getHash(), tx);
				// This can be null if we are stopped.  We'll scan this transaction when we start again
				if (fetchService != null)
					fetchService.schedule(new Lookup(tx), 0, TimeUnit.SECONDS);
			}
		} finally {
			lock.unlock();
			wallet.unlock();
		}
	}

	// True iff we can derive the color output information from the inputs
	private boolean tryLocalLookup(Transaction tx, boolean overrideFound) {
		List<ClientColorTrack> found = Lists.newArrayList();
		// Find all colors that know about inputs
		for (TransactionInput input : tx.getInputs()) {
			boolean isFound = false;
			for (ClientColorTrack track : tracks) {
				if (track.isColored(input.getOutpoint())) {
					found.add(track);
					isFound = true;
				}
			}
			// FIXME uncolored bitcoin inputs will always result in !isFound
			if (!overrideFound && !isFound)
				return false;
		}
		for (ClientColorTrack track : found) {
			track.add(tx);
		}
		return true;
	}

	class Lookup implements Runnable {
		private final Transaction tx;
		private int tries = 0;

		Lookup(Transaction tx) {
			this.tx = tx;
		}

		@Override
		public void run() {
			if (tx.getConfidence(wallet.getContext()).getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
				for (TransactionOutput output : tx.getOutputs()) {
					TransactionOutPoint point = output.getOutPointFor();
					if (colorKeyChain.isOutputToMe(output)) {
						if (handleOutpoint(point)) return;
					}
				}
			} else {
				// Unconfirmed transaction
				// Lookup all inputs, and derive color from that
				for (TransactionInput input : tx.getInputs()) {
					if (handleOutpoint(input.getOutpoint())) return;
				}
				tryLocalLookup(tx, true);
			}
			notifyTransactionDone(tx);
		}

		// True if retry initiated
		private boolean handleOutpoint(TransactionOutPoint point) {
			try {
				if (!contains(point))
					doOutPoint(point);
			} catch (SerializationException e) {
				log.error("serialization problem", e);
				retry();
				return true;
			} catch (TemporaryFailureException e) {
				log.warn("tempfail " + point);
				retry();
				return true;
			}
			return false;
		}

		private void retry() {
			tries++;
			// Jitter 2 seconds + 2 ** tries
			long delay = (long) (Math.random() * 2000 + 1000 * Math.pow(2, Math.min(tries, 7)));
			fetchService.schedule(this, delay, TimeUnit.MILLISECONDS);
		}

		private void doOutPoint(TransactionOutPoint outPoint) throws SerializationException, TemporaryFailureException {
			ColorProof proof = fetcher.fetch(outPoint);
			log.info("after fetch: " + getPendingCount() + " pending");
			if (proof == null)
				return;
			boolean found = false;
			lock.lock();
			try {
				for (ClientColorTrack track : tracks) {
					if (track.definition.equals(proof.getDefinition())) {
						track.add(proof);
						found = true;
					}
				}
				if (!found) {
					// TODO handle new asset type
					log.warn("Unknown asset type fetched " + proof.getDefinition().getHash());
				}
			} finally {
				lock.unlock();
			}
		}

		private void notifyTransactionDone(Transaction tx) {
			lock.lock();
			Collection<SettableFuture<Transaction>> futures;
			try {
				pending.remove(tx.getHash());
				futures = unknownTransactionFutures.removeAll(tx);
			} finally {
				lock.unlock();
			}
			wallet.saveLater();
			if (futures != null) {
				for (SettableFuture<Transaction> future : futures) {
					future.set(tx);
				}
			}
		}
	}

	static class TemporaryFailureException extends Exception {
	}

	@JsonDeserialize(keyUsing = HexKeyDeserializer.class)
	public static class ProofMap extends HashMap<HashCode, byte[]> {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class OutPointResponse {
		public String status;
		public String error;
		public String details;
		@JsonDeserialize(keyUsing = HexKeyDeserializer.class)
		public Map<HashCode, ProofMap> proofs;

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
		private static SSLContext sslContext;
		private final URI base;
		private final NetworkParameters params;
		CloseableHttpClient httpclient;
		ObjectMapper mapper = new ObjectMapper();

		public Fetcher(URI base, NetworkParameters params) {
			this.base = base;
			this.params = params;
			initClient();
		}

		Fetcher(URI base, NetworkParameters params, CloseableHttpClient httpclient) {
			this.base = base;
			this.params = params;
			this.httpclient = httpclient;
		}

		public static void setSslContext(SSLContext sslContext) {
			Fetcher.sslContext = sslContext;
		}

		private void initClient() {
			RequestConfig config = RequestConfig.custom()
					.setConnectionRequestTimeout(NETWORK_TIMEOUT)
					.setConnectTimeout(NETWORK_TIMEOUT)
					.setSocketTimeout(NETWORK_TIMEOUT)
					.build();
			httpclient = HttpClients.custom()
					.setUserAgent("SmartColors-java-" + SmartColors.getVersion())
					.setDefaultRequestConfig(config)
					.setSslcontext(sslContext)
					.build();
		}

		public ColorProof fetch(TransactionOutPoint point) throws SerializationException, TemporaryFailureException {
			String relative = "outpoint/" + point.getHash() + "/" + point.getIndex();
			log.info("fetching " + relative);
			HttpGet get = new HttpGet(base.resolve(relative));

			CloseableHttpResponse response = null;
			try {
				response = httpclient.execute(get);
				StatusLine statusLine = response.getStatusLine();
				if (statusLine.getStatusCode() >= 300) {
					log.warn("got status " + statusLine);
					throw new TemporaryFailureException();
				}
				OutPointResponse res = mapper.readValue(response.getEntity().getContent(), OutPointResponse.class);

				if (res.proofs == null) {
					log.warn("fetch failure " + res.status + " " + res.details);
					throw new TemporaryFailureException();
				}

				log.info("fetch success " + res.status + ", " + res.proofs.size() + " proofs");

				for (ProofMap map : res.proofs.values()) {
					// TODO this only returns first
					for (byte[] bytes : map.values()) {
						return ColorProof.deserialize(params, new BytesDeserializer(bytes));
					}
				}
			} catch (IOException e) {
				// temporary failure
				log.warn("got IOException " + e.getMessage());
				throw new TemporaryFailureException();
			} catch (StackOverflowError e) {
				log.error("could not deserialize proof, deeming UNKNOWN");
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
		stop();
	}

    @Override
    public List<ListenableFuture<Transaction>> rescanUnknown(Wallet _wallet, ColorKeyChain colorKeyChain) {
        SmartWallet wallet = (SmartWallet)_wallet;
		List<ListenableFuture<Transaction>> futures = Lists.newArrayList();
        wallet.lock();
        lock.lock();
		log.info("before rescan " + pending.size() + " pending");
        try {
            List<TransactionOutput> all = wallet.calculateAllSpendCandidates(false, false);
            for (TransactionOutput output : all) {
                if (colorKeyChain.isOutputToMe(output) && !contains(output.getOutPointFor())) {
                    SettableFuture<Transaction> future = SettableFuture.create();
                    futures.add(future);
                    Transaction tx = output.getParentTransaction();
                    unknownTransactionFutures.put(tx, future);
                    onTransaction(wallet, tx);
                }
            }
			log.info("after rescan start " + pending.size() + " pending");
        } finally {
            lock.unlock();
            wallet.unlock();
        }

		wallet.saveLater();
		return futures;
	}
}