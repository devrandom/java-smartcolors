package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColorProofTest {
	public static final Script EMPTY_SCRIPT = new Script(new byte[0]);
	public static final Coin ASSET_COIN_ONE = Coin.valueOf(SmartColors.addMsbdropValuePadding(1, 0));
	private NetworkParameters params;

	@Before
	public void setUp() {
		params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
	}

	@Test
	public void simpleAdd() {
		Transaction genesisTx = new Transaction(params);
		genesisTx.addOutput(ASSET_COIN_ONE, new Script(new byte[0]));
		TransactionOutPoint genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		TxOutGenesisPoint genesis = new TxOutGenesisPoint(params, genesisOutPoint);
		ColorDefinition def = new ColorDefinition(Sets.newHashSet((GenesisPoint)genesis));
		ColorProof proof = new ColorProof(def);
		assertTrue(proof.getOutputs().isEmpty());
		assertTrue(proof.getUnspentOutputs().isEmpty());

		proof.add(genesisTx);
		HashMap<TransactionOutPoint, Long> expectedAll = Maps.newHashMap();
		HashMap<TransactionOutPoint, Long> expectedUnspent = Maps.newHashMap();
		expectedAll.put(genesisOutPoint, 1L);
		expectedUnspent.put(genesisOutPoint, 1L);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());

		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx2OutPoint = new TransactionOutPoint(params, 0, tx2);
		expectedAll.put(tx2OutPoint, 1L);
		expectedUnspent.remove(genesisOutPoint);
		expectedUnspent.put(tx2OutPoint, 1L);
		proof.add(tx2);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());

		Transaction tx3 = new Transaction(params);
		tx3.addInput(tx2.getOutput(0));
		tx3.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx3OutPoint = new TransactionOutPoint(params, 0, tx3);
		expectedAll.put(tx3OutPoint, 1L);
		expectedUnspent.remove(tx2OutPoint);
		expectedUnspent.put(tx3OutPoint, 1L);
		proof.add(tx3);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());

		Transaction tx4 = new Transaction(params);
		tx4.addInput(tx3.getOutput(0));
		tx4.getInput(0).setSequenceNumber(0); // Destroy color
		tx4.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		expectedUnspent.remove(tx3OutPoint);
		proof.add(tx4);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());
		assertEquals(0, expectedUnspent.size());
		assertEquals(3, expectedAll.size());

		proof.undoLast();
		proof.undoLast();
		proof.undoLast();
		expectedAll = Maps.newHashMap();
		expectedUnspent = Maps.newHashMap();
		expectedAll.put(genesisOutPoint, 1L);
		expectedUnspent.put(genesisOutPoint, 1L);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());
		proof.undoLast();
		assertTrue(proof.getOutputs().isEmpty());
		assertTrue(proof.getUnspentOutputs().isEmpty());
	}

	@Test
	public void complexAdd() {
		// TODO
	}
}
