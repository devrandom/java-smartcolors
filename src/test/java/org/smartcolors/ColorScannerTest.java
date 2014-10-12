package org.smartcolors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.bitcoinj.core.AbstractBlockChain;
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
		assertTrue(getBloomFilter().contains(genesisOutPoint.bitcoinSerialize()));
		assertTrue(scanner.isTransactionRelevant(genesisTx));
		scanner.receiveFromBlock(genesisTx, genesisBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		assertEquals(1, scanner.getBloomFilterElementCount());
		assertTrue(getBloomFilter().contains(genesisOutPoint.bitcoinSerialize()));

		// Transaction splitting genesis
		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(coin(4), EMPTY_SCRIPT);
		tx2.addOutput(coin(6), EMPTY_SCRIPT);
		StoredBlock block2 = FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock;
		scanner.receiveFromBlock(tx2, block2, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		assertEquals(3, scanner.getBloomFilterElementCount());
		assertTrue(getBloomFilter().contains(tx2.getOutput(0).getOutPointFor().bitcoinSerialize()));
		assertTrue(getBloomFilter().contains(tx2.getOutput(1).getOutPointFor().bitcoinSerialize()));

		Transaction tx3 = new Transaction(params);
		tx3.addInput(tx2.getOutput(1));
		tx3.addOutput(coin(1), EMPTY_SCRIPT);
		tx3.addOutput(coin(100), EMPTY_SCRIPT);
		StoredBlock block3 = FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock;
		scanner.getPeerEventListener().onTransaction(null, tx3);
		scanner.notifyTransactionIsInBlock(tx3.getHash(), block3, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		assertEquals(5, scanner.getBloomFilterElementCount());
		assertEquals(5, (long)proof.getColor(tx3.getOutput(1).getOutPointFor()));

		// Reorg last two blocks out of existence
		scanner.reorganize(null, Lists.newArrayList(block2, block3), Lists.<StoredBlock>newArrayList());
		assertEquals(null, proof.getColor(tx3.getOutput(0).getOutPointFor()));
		assertEquals(null, proof.getColor(tx3.getOutput(1).getOutPointFor()));
		assertEquals(null, proof.getColor(tx2.getOutput(0).getOutPointFor()));
		assertEquals(null, proof.getColor(tx2.getOutput(1).getOutPointFor()));
		assertEquals(10, (long)proof.getColor(genesisTx.getOutput(0).getOutPointFor()));
	}

	private BloomFilter getBloomFilter() {
		return scanner.getBloomFilter(10, 1e-12, (long) (Math.random() * Long.MAX_VALUE));
	}

	private Coin coin(long value) {
		return Coin.valueOf(SmartColors.addMsbdropValuePadding(value,0));
	}
}