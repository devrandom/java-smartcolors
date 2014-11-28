package org.smartcolors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.KeyChain;
import org.easymock.Capture;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.ColorProof;
import org.smartcolors.core.GenesisOutPointColorProof;
import org.smartcolors.core.SmartColors;
import org.smartcolors.core.TransferColorProof;
import org.smartcolors.protos.Protos;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import static junit.framework.Assert.fail;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClientColorScannerTest extends ColorTest {
	private ClientColorScanner scanner;
	private ClientColorTrack track;
	private URI base;
	private ObjectMapper mapper;
	private ECKey colorKey;
	private HashMap<TransactionOutPoint, ColorProof> proofs;
	private SmartwalletExtension ext;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		mapper = new ObjectMapper();
		base = new URI("http://localhost:8888/");
		scanner = new ClientColorScanner(params, base);
		scanner.addDefinition(def);
		track = (ClientColorTrack) scanner.getColorTrackByDefinition(def);
		colorKey = new ECKey();
		colorChain = new ColorKeyChain(new SecureRandom(), 128, "", 0) {
			// Hack - delegate to the current wallet
			@Override
			public boolean isOutputToMe(TransactionOutput output) {
				if (output.getScriptPubKey().isSentToAddress())
					return colorKey.getPubKeyHash().equals(output.getScriptPubKey().getPubKeyHash());
				else if (output.getScriptPubKey().isSentToRawPubKey())
					return Arrays.equals(colorKey.getPubKey(), output.getScriptPubKey().getPubKey());
				return false;
			}
		};
		wallet = new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return Arrays.equals(pubkey, colorKey.getPubKey()) || super.isPubKeyMine(pubkey);
			}
		};

		scanner.setColorKeyChain(colorChain);
		proofs = Maps.newHashMap();
		ext = new SmartwalletExtension(params);
	}

	@Test
	public void addNotMine() {
		wallet = new Wallet(params);

		Transaction tx2 = makeTx2(new ECKey());
		scanner.onReceive(wallet, tx2);
		assertTrue(scanner.pending.isEmpty());
	}

	@Test
	public void add() {
		Transaction tx2 = makeTx2(colorKey);
		scanner.onReceive(wallet, tx2);
		assertEquals(1, scanner.pending.size());
	}

	@Test
	public void run() throws Exception {
		Transaction tx2 = makeTx2(colorKey);

		ClientColorScanner.Fetcher fetcher = createMock(ClientColorScanner.Fetcher.class);
		final ColorProof proof = createMock(ColorProof.class);
		expect(proof.getDefinition()).andStubReturn(def);
		expect(proof.getHash()).andStubReturn(HashCode.fromBytes(new byte[32]));
		expect(proof.getOutPoint()).andStubReturn(tx2.getOutput(0).getOutPointFor());
		expect(proof.getQuantity()).andStubReturn(10L);
		proof.validate();
		expectLastCall();
		scanner.setFetcher(fetcher);

		final CyclicBarrier barrier = new CyclicBarrier(2);
		final TransactionOutPoint point = tx2.getOutput(0).getOutPointFor();
		expect(fetcher.fetch(point)).andStubAnswer(new IAnswer<ColorProof>() {
			@Override
			public ColorProof answer() throws Throwable {
				barrier.await();
				return proof;
			}
		});
		fetcher.stop();
		expectLastCall();
		replay(fetcher, proof);
		scanner.onReceive(wallet, tx2);
		barrier.await();
		Utils.sleep(100);
		ListenableFuture<Transaction> future = scanner.getTransactionWithKnownAssets(tx2, wallet, colorChain);
		Transaction ftx = future.get();
		assertEquals(tx2, ftx);
		scanner.stop();
		verify(fetcher, proof);
		assertTrue(scanner.pending.isEmpty());
		assertTrue(track.proofs.containsKey(proof.getHash()));
		future = scanner.getTransactionWithKnownAssets(tx2, wallet, colorChain);
		assertTrue(future.isDone());
		Map<ColorDefinition, Long> change = scanner.getNetAssetChange(tx2, wallet, colorChain);
		assertEquals(1, change.size());
		assertEquals(10L, (long)change.get(def));
		wallet.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		Transaction tx3 = new Transaction(params);
		tx3.addOutput(Coin.CENT, wallet.currentKey(KeyChain.KeyPurpose.RECEIVE_FUNDS));
		wallet.receiveFromBlock(tx3, FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);

		Map<ColorDefinition, Long> balances = scanner.getBalances(wallet, colorChain);
		assertEquals(10L, (long) balances.get(def));
		assertEquals(Coin.CENT.getValue(), (long)balances.get(scanner.getBitcoinDefinition()));
		assertTrue(scanner.fetchService.shutdownNow().isEmpty());
	}

	@Test
	public void testGetNetAssetChange() throws Exception {
		GenesisOutPointColorProof genesisProof = new GenesisOutPointColorProof(def, genesisTx.getOutput(0).getOutPointFor());
		proofs.put(genesisTx.getOutput(0).getOutPointFor(), genesisProof);
		track.add(genesisProof);

		Transaction tx2 = makeTx2(colorKey);
		TransferColorProof tx2Proof = new TransferColorProof(def, tx2, 0, Maps.newHashMap(proofs));
		track.add(tx2Proof);
		proofs.put(tx2.getOutput(0).getOutPointFor(), tx2Proof);
		wallet.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		Map<ColorDefinition, Long> expected = Maps.newHashMap();
		Map<ColorDefinition, Long> res = scanner.getNetAssetChange(tx2, wallet, colorChain);
		expected.put(def, 5L);
		assertEquals(expected, res);

		Transaction tx3 = new Transaction(params);
		tx3.addInput(SmartColors.makeAssetInput(tx3, tx2, 0));
		tx3.addOutput(Utils.makeAssetCoin(2), ScriptBuilder.createOutputScript(colorKey));
		tx3.addOutput(Utils.makeAssetCoin(3), ScriptBuilder.createOutputScript(privkey1));
		tx3.addOutput(Coin.ZERO, opReturnScript);
		TransferColorProof tx3Proof = new TransferColorProof(def, tx3, 0, Maps.newHashMap(proofs));
		track.add(tx3Proof);
		proofs.put(tx3.getOutput(0).getOutPointFor(), tx3Proof);
		wallet.receiveFromBlock(tx3, FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);

		expected.clear();
		res = scanner.getNetAssetChange(tx3, wallet, colorChain);
		expected.put(def, -3L);
		assertEquals(expected, res);

		ClientColorScanner scanner1 = new ClientColorScanner(params, base);
		scanner1.addDefinition(def);
		Protos.ColorScanner proto = ext.serializeScanner(scanner);
		ext.deserializeScannerClient(params, proto, scanner1);
		res = scanner1.getNetAssetChange(tx3, wallet, colorChain);
		assertEquals(expected, res);
	}

	@Test
	public void json() throws Exception {
		String fixture = FixtureHelpers.fixture("tracker1.json");
		ClientColorScanner.OutPointResponse res =
				mapper.readValue(fixture, ClientColorScanner.OutPointResponse.class);
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

	@Test
	public void fetcher() throws Exception {
		TransactionOutPoint point = new TransactionOutPoint(params, 123, Sha256Hash.create(new byte[]{0, 1, 2, 3}));
		CloseableHttpClient client = createMock(CloseableHttpClient.class);
		ClientColorScanner.Fetcher fetcher = new ClientColorScanner.Fetcher(base, params, client);
		Capture<HttpGet> cap = new Capture<HttpGet>();
		CloseableHttpResponse response = createMock(CloseableHttpResponse.class);

		// JSON parse error
		mockResponse(client, cap, response, "");
		replay(client, response);
		try {
			fetcher.fetch(point);
			fail();
		} catch (ClientColorScanner.TemporaryFailureException ex) {
			// expected
		}
		verify(client, response);
		assertEquals(base.resolve("outpoint/" + point.getHash() + "/123"), cap.getValue().getURI());

		reset(client, response);

		// Missing status
		mockResponse(client, cap, response, "{}");
		replay(client, response);
		try {
			fetcher.fetch(point);
			fail();
		} catch (ClientColorScanner.TemporaryFailureException ex) {
			// expected
		}
		verify(client, response);

		reset(client, response);

		// Not found
		mockResponse(client, cap, response, "{\"error\":\"NOT_FOUND\"}");
		replay(client, response);
		try {
			fetcher.fetch(point);
			fail();
		} catch (ClientColorScanner.TemporaryFailureException ex) {
			// expected
		}
		verify(client, response);

		reset(client, response);

		// Not colored
		mockResponse(client, cap, response, "{\"error\":\"NOT_COLORED\"}");
		replay(client, response);
		ColorProof res = fetcher.fetch(point);
		verify(client, response);
		assertNull(res);

		reset(client, response);

		// Another way to say not-colored
		mockResponse(client, cap, response, "{\"status\":\"OK\"}");
		replay(client, response);
		res = fetcher.fetch(point);
		verify(client, response);
		assertNull(res);

		reset(client, response);

		// Yet another way to say not-colored
		mockResponse(client, cap, response, "{\"status\":\"OK\", \"colordefs\":{}}");
		replay(client, response);
		res = fetcher.fetch(point);
		verify(client, response);
		assertNull(res);

		String fixture = FixtureHelpers.fixture("tracker1.json");
		reset(client, response);

		// Not colored
		mockResponse(client, cap, response, fixture);
		replay(client, response);
		res = fetcher.fetch(point);
		verify(client, response);
		assertEquals("fdd29f08a159af536cfd1370ed7d4f303dc1b21685afb6e2bc0f3862d7f5e42d", res.getHash().toString());
	}

	@Test
	public void fetcherBadStatus() throws Exception {
		TransactionOutPoint point = new TransactionOutPoint(params, 123, Sha256Hash.create(new byte[]{0, 1, 2, 3}));
		CloseableHttpClient client = createMock(CloseableHttpClient.class);
		ClientColorScanner.Fetcher fetcher = new ClientColorScanner.Fetcher(base, params, client);
		CloseableHttpResponse response = createMock(CloseableHttpResponse.class);
		expect(response.getStatusLine()).andReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 400, "BAD"));
		response.close();
		expectLastCall();
		expect(client.execute(anyObject(HttpGet.class))).andReturn(response);
		client.close();
		expectLastCall();
		replay(client, response);
		try {
			fetcher.fetch(point);
			fail();
		} catch (ClientColorScanner.TemporaryFailureException ex) {
			// expected
		}
		fetcher.stop();
		verify(client, response);
	}

	private void mockResponse(CloseableHttpClient client, Capture<HttpGet> cap, CloseableHttpResponse response, String body) throws IOException {
		expect(response.getStatusLine()).andReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		expect(response.getEntity()).andReturn(new StringEntity(body));
		response.close();
		expectLastCall();
		expect(client.execute(capture(cap))).andReturn(response);
	}
}