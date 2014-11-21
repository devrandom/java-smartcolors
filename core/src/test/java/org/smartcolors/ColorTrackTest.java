package org.smartcolors;

import com.google.common.collect.Maps;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.GenesisOutPointsMerbinnerTree;
import org.smartcolors.core.GenesisScriptMerbinnerTree;
import org.smartcolors.core.SmartColors;
import org.smartcolors.protos.Protos;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.smartcolors.core.SmartColors.makeAssetInput;

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
		GenesisOutPointsMerbinnerTree outPoints = makeTree(genesisOutPoint);
		ColorDefinition def = new ColorDefinition(params, outPoints, new GenesisScriptMerbinnerTree());
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
		tx2.addInput(makeAssetInput(tx2, genesisTx, 0));
		tx2.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx2OutPoint = new TransactionOutPoint(params, 0, tx2);
		expectedAll.put(tx2OutPoint, 1L);
		expectedUnspent.remove(genesisOutPoint);
		expectedUnspent.put(tx2OutPoint, 1L);
		proof.add(tx2);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());

		Transaction tx3 = new Transaction(params);
		tx3.addInput(makeAssetInput(tx3, tx2, 0));
		tx3.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx3OutPoint = new TransactionOutPoint(params, 0, tx3);
		expectedAll.put(tx3OutPoint, 1L);
		expectedUnspent.remove(tx2OutPoint);
		expectedUnspent.put(tx3OutPoint, 1L);
		proof.add(tx3);
		assertEquals(expectedAll, proof.getOutputs());
		assertEquals(expectedUnspent, proof.getUnspentOutputs());

		Transaction tx4 = new Transaction(params);
		tx4.addInput(makeAssetInput(tx4, tx3, 0));
		tx4.getInput(0).setSequenceNumber(0x7E); // Destroy color
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

	private GenesisOutPointsMerbinnerTree makeTree(TransactionOutPoint genesisOutPoint) {
		Map<TransactionOutPoint, Long> nodes = Maps.newHashMap();
		nodes.put(genesisOutPoint, 0L);
		return new GenesisOutPointsMerbinnerTree(params, nodes);
	}

	@Test
	public void serialize() {
		Transaction genesisTx = new Transaction(params);
		genesisTx.addOutput(ASSET_COIN_ONE, new Script(new byte[0]));
		TransactionOutPoint genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		GenesisOutPointsMerbinnerTree outPoints = makeTree(genesisOutPoint);
		ColorDefinition def = new ColorDefinition(params, outPoints, new GenesisScriptMerbinnerTree());
		ColorTrack proof = new ColorTrack(def);

		proof.add(genesisTx);

		Transaction tx2 = new Transaction(params);
		tx2.addInput(makeAssetInput(tx2, genesisTx, 0));
		tx2.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx2OutPoint = new TransactionOutPoint(params, 0, tx2);
		proof.add(tx2);

		Transaction tx3 = new Transaction(params);
		tx3.addInput(makeAssetInput(tx3, tx2, 0));
		tx3.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		TransactionOutPoint tx3OutPoint = new TransactionOutPoint(params, 0, tx3);
		proof.add(tx3);

		Transaction tx4 = new Transaction(params);
		tx4.addInput(makeAssetInput(tx4, tx3, 0));
		tx4.getInput(0).setSequenceNumber(0x7E); // Destroy color
		tx4.addOutput(ASSET_COIN_ONE, EMPTY_SCRIPT);
		proof.add(tx4);

		SmartwalletExtension ext = new SmartwalletExtension(params);
		Protos.ColorProof proofProto = ext.serializeProof(proof);
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
		Protos.ColorProof proofProto2 = ext.serializeProof(proof);
		ColorTrack proof2 = new ColorTrack(def);
		SmartwalletExtension.deserializeProof(params, proofProto2, proof2);
		assertEquals(proof.getStateHash(), proof2.getStateHash());
	}

	@Test
	public void complexAdd() {
		// TODO
	}
}
