package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.testing.FakeTxBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColorScannerTest {
	public static final Script EMPTY_SCRIPT = new Script(new byte[0]);

	private NetworkParameters params;
	private ColorScanner scanner;
	private Transaction genesisTx;
	private TransactionOutPoint genesisOutPoint;
	private MemoryBlockStore blockStore;
	private StoredBlock genesisBlock;
	private ColorProof proof;
	private ColorDefinition def;

	@Before
	public void setUp() throws Exception {
		params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
		blockStore = new MemoryBlockStore(params);
		genesisTx = new Transaction(params);
		genesisTx.addInput(Sha256Hash.ZERO_HASH, 0, EMPTY_SCRIPT);
		genesisTx.addOutput(Utils.makeAssetCoin(10), new Script(new byte[0]));
		genesisBlock = FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock;
		genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		TxOutGenesisPoint genesis = new TxOutGenesisPoint(params, genesisOutPoint);
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		points.add(genesis);
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put("name", "widgets");
		def = new ColorDefinition(points, metadata);
		proof = new ColorProof(def);
		scanner = new ColorScanner();
		scanner.addProof(proof);
	}

	@Test
	public void testBloomFilter() throws Exception {
		// Genesis
		assertEquals(1, scanner.getBloomFilterElementCount());
		assertTrue(getBloomFilter().contains(org.bitcoinj.core.Utils.HEX.decode("534d415254415353")));
	}

	@Test
	public void testGetNetAssetChangeUnknown() {
		Wallet wallet = new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return true;
			}
		};
		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(new ECKey()));
		Map<ColorDefinition, Long> res = scanner.getNetAssetChange(tx2, wallet);
		Map<ColorDefinition, Long> expected = Maps.newHashMap();
		expected.put(ColorDefinition.UNKNOWN, 5L);
		assertEquals(expected, res);
	}

	@Test
	public void testGetNetAssetChange() {
		final ECKey myKey = new ECKey();
		scanner.receiveFromBlock(genesisTx, FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		Wallet wallet = new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return Arrays.equals(pubkey, myKey.getPubKey());
			}
		};

		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(myKey));
		scanner.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		Map<ColorDefinition, Long> expected = Maps.newHashMap();
		Map<ColorDefinition, Long> res = scanner.getNetAssetChange(tx2, wallet);
		expected.put(def, 5L);
		assertEquals(expected, res);


		Transaction tx3 = new Transaction(params);
		tx3.addInput(tx2.getOutput(0));
		tx3.addOutput(Utils.makeAssetCoin(2), ScriptBuilder.createOutputScript(myKey));
		tx3.addOutput(Utils.makeAssetCoin(3), ScriptBuilder.createOutputScript(new ECKey()));
		scanner.receiveFromBlock(tx3, FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet.receiveFromBlock(tx3, FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);

		expected.clear();
		res = scanner.getNetAssetChange(tx3, wallet);
		expected.put(def, -3L);
		assertEquals(expected, res);
	}

	private BloomFilter getBloomFilter() {
		return scanner.getBloomFilter(10, 1e-12, (long) (Math.random() * Long.MAX_VALUE));
	}
}