package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;
import org.smartcolors.protos.Protos;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ColorTrackTest {
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
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		points.add(genesis);
		ColorDefinition def = new ColorDefinition(points);
		ColorTrack proof = new ColorTrack(def);
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
	public void serialize() {
		Transaction genesisTx = new Transaction(params);
		genesisTx.addOutput(ASSET_COIN_ONE, new Script(new byte[0]));
		TransactionOutPoint genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		TxOutGenesisPoint genesis = new TxOutGenesisPoint(params, genesisOutPoint);
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		points.add(genesis);
		ColorDefinition def = new ColorDefinition(points);
		ColorTrack proof = new ColorTrack(def);

		proof.add(genesisTx);

		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx2OutPoint = new TransactionOutPoint(params, 0, tx2);
		proof.add(tx2);

		Transaction tx3 = new Transaction(params);
		tx3.addInput(tx2.getOutput(0));
		tx3.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx3OutPoint = new TransactionOutPoint(params, 0, tx3);
		proof.add(tx3);

		Transaction tx4 = new Transaction(params);
		tx4.addInput(tx3.getOutput(0));
		tx4.getInput(0).setSequenceNumber(0); // Destroy color
		tx4.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		proof.add(tx4);

		Protos.ColorProof proofProto = SmartwalletExtension.serializeProof(proof);
		ColorTrack proof1 = new ColorTrack(def);
		final Map<Sha256Hash, Transaction> txs = Maps.newHashMap();
		txs.put(genesisTx.getHash(), genesisTx);
		txs.put(tx2.getHash(), tx2);
		txs.put(tx3.getHash(), tx3);
		txs.put(tx4.getHash(), tx4);
		Wallet wallet = new Wallet(params) {
			@Nullable
			@Override
			public Transaction getTransaction(Sha256Hash hash) {
				return txs.get(hash);
			}
		};
		SmartwalletExtension.deserializeProof(params, proofProto, proof1);
		assertEquals(proof.getStateHash(), proof1.getStateHash());
		proof.undoLast();
		Protos.ColorProof proofProto2 = SmartwalletExtension.serializeProof(proof);
		ColorTrack proof2 = new ColorTrack(def);
		SmartwalletExtension.deserializeProof(params, proofProto2, proof2);
		assertEquals(proof.getStateHash(), proof2.getStateHash());
	}

	@Test
	public void complexAdd() {
		// TODO
	}
}
