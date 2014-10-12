package org.smartcolors;

import com.google.common.collect.Sets;

import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.testing.FakeTxBuilder;
import org.junit.Before;
import org.junit.Test;

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

	@Before
	public void setUp() throws Exception {
		params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
		blockStore = new MemoryBlockStore(params);
		genesisTx = new Transaction(params);
		genesisTx.addOutput(coin(10), new Script(new byte[0]));
		genesisBlock = FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock;
		genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		TxOutGenesisPoint genesis = new TxOutGenesisPoint(params, genesisOutPoint);
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		points.add(genesis);
		ColorDefinition def = new ColorDefinition(points);
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

	private BloomFilter getBloomFilter() {
		return scanner.getBloomFilter(10, 1e-12, (long) (Math.random() * Long.MAX_VALUE));
	}

	private Coin coin(long value) {
		return Coin.valueOf(SmartColors.addMsbdropValuePadding(value,0));
	}
}