package org.smartcolors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.ListenableFuture;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.ColorProof;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClientColorScannerTest extends ColorTest {
	private ClientColorScanner scanner;
	private ClientColorTrack track;
	private URI base;
	private ObjectMapper mapper;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		mapper = new ObjectMapper();
		base = new URI("http://localhost:8888/");
		scanner = new ClientColorScanner(params, base);
		scanner.addDefinition(def);
		track = (ClientColorTrack) scanner.getColorTrackByDefinition(def);
		colorChain = new ColorKeyChain(new SecureRandom(), 128, "", 0) {
			// Hack - delegate to the current wallet
			@Override
			public boolean isOutputToMe(TransactionOutput output) {
				if (output.getScriptPubKey().isSentToAddress())
					return wallet.isPubKeyHashMine(output.getScriptPubKey().getPubKeyHash());
				else if (output.getScriptPubKey().isSentToRawPubKey())
					return wallet.isPubKeyMine(output.getScriptPubKey().getPubKey());
				return false;
			}
		};
	}

	@Test
	public void addNotMine() {
		wallet = new Wallet(params);

		Transaction tx2 = makeTx2(new ECKey());
		scanner.onReceive(wallet, tx2);
		assertTrue(scanner.lookups.isEmpty());
		assertTrue(scanner.pending.isEmpty());
	}

	@Test
	public void add() {
		ECKey myKey = new ECKey();
		wallet = makeWallet(myKey);

		Transaction tx2 = makeTx2(myKey);
		scanner.onReceive(wallet, tx2);
		assertEquals(2, scanner.lookups.size());
		assertEquals(1, scanner.pending.size());
	}

	private Wallet makeWallet(final ECKey myKey) {
		return new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return Arrays.equals(pubkey, myKey.getPubKey());
			}
		};
	}

	@Test
	public void run() throws Exception {
		ECKey myKey = new ECKey();
		wallet = makeWallet(myKey);
		Transaction tx2 = makeTx2(myKey);

		ClientColorScanner.Fetcher fetcher = createMock(ClientColorScanner.Fetcher.class);
		final ColorProof proof = createMock(ColorProof.class);
		expect(proof.getDefinition()).andStubReturn(def);
		expect(proof.getHash()).andStubReturn(HashCode.fromBytes(new byte[32]));
		expect(proof.getOutPoint()).andStubReturn(tx2.getOutput(0).getOutPointFor());
		expect(proof.getQuantity()).andStubReturn(10L);
		proof.validate();
		expectLastCall();
		scanner.setFetcher(fetcher);
		scanner.start();

		final CyclicBarrier barrier = new CyclicBarrier(2);
		final TransactionOutPoint point = tx2.getOutput(0).getOutPointFor();
		expect(fetcher.fetch(point)).andStubAnswer(new IAnswer<ColorProof>() {
			@Override
			public ColorProof answer() throws Throwable {
				barrier.await();
				return proof;
			}
		});
		replay(fetcher, proof);
		scanner.onReceive(wallet, tx2);
		barrier.await();
		Utils.sleep(100);
		ListenableFuture<Transaction> future = scanner.getTransactionWithKnownAssets(tx2, wallet, colorChain);
		Transaction ftx = future.get();
		assertEquals(tx2, ftx);
		verify(fetcher, proof);
		scanner.stop();
		assertTrue(scanner.lookups.isEmpty());
		assertTrue(scanner.pending.isEmpty());
		assertTrue(track.proofs.containsKey(proof.getHash()));
		future = scanner.getTransactionWithKnownAssets(tx2, wallet, colorChain);
		assertTrue(future.isDone());
		Map<ColorDefinition, Long> change = scanner.getNetAssetChange(tx2, wallet, colorChain);
		assertEquals(1, change.size());
		assertEquals(10L, (long)change.get(def));
	}

	@Test
	public void json() throws Exception {
		String fixture = FixtureHelpers.fixture("tracker1.json");
		ClientColorScanner.OutPointResponse res =
				mapper.readValue(fixture, ClientColorScanner.OutPointResponse.class);
		System.out.println(res);
	}

	@Ignore
	@Test
	public void http() throws Exception {
		final ClientColorScanner.Fetcher fetcher = new ClientColorScanner.Fetcher(base, params);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println("start");
					ColorProof res = fetcher.fetch(genesisTx.getOutput(0).getOutPointFor());
					System.out.println("stop " + res);
				} catch (Exception e) {
					System.out.println(e);
					Throwables.propagate(e);
				}
			}
		}).start();
		Utils.sleep(2 * 1000);
//		System.out.println("stopping");
//		fetcher.stop();
		Utils.sleep(60*1000);
	}
}