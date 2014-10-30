package org.smartcolors;

import com.google.common.collect.Sets;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.KeyChainGroup;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CoinSelectorTest extends ColorTest {
	private static final Logger log = LoggerFactory.getLogger(CoinSelectorTest.class);

	private DeterministicKey colorKey;
	private BitcoinCoinSelector bitcoinSelector;
	private AssetCoinSelector assetSelector;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		colorChain =
				ColorKeyChain.builder()
						.random(new SecureRandom())
						.build();
		DeterministicKeyChain chain =
				DeterministicKeyChain.builder()
						.seed(colorChain.getSeed())
						.build();
		KeyChainGroup group = new KeyChainGroup(params);
		group.setLookaheadSize(20);
		group.setLookaheadThreshold(7);
		group.addAndActivateHDChain(colorChain);
		group.addAndActivateHDChain(chain);
		wallet = new Wallet(params, group);
		colorKey = colorChain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
		bitcoinSelector = new BitcoinCoinSelector(colorChain);
		assetSelector = new AssetCoinSelector(colorChain, scanner.getColorProofByHash(def.getHash()));
		scanner.receiveFromBlock(genesisTx, FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
	}

	@Test
	public void testSelection() throws InsufficientMoneyException {
		// Incoming asset
		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(Utils.makeAssetCoin(8), ScriptBuilder.createOutputScript(colorKey));
		tx2.addOutput(Coin.ZERO, opReturnScript);
		receiveTransaction(tx2);

		// Partially spend asset
		Transaction tx3 = new Transaction(params);
		tx3.addInput(tx2.getOutput(0));
		tx3.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(colorKey));
		tx3.addOutput(Utils.makeAssetCoin(3), ScriptBuilder.createOutputScript(new ECKey()));
		tx3.addOutput(Coin.ZERO, opReturnScript);
		receiveTransaction(tx3);

		// Incoming bitcoin
		Transaction tx4 = new Transaction(params);
		tx4.addInput(genesisTx.getOutput(0));
		tx4.addOutput(Coin.COIN, ScriptBuilder.createOutputScript(wallet.currentReceiveKey()));
		tx4.addOutput(Coin.ZERO, opReturnScript); // Spurious OP_RETURN marker
		receiveTransaction(tx4);

		AssetCoinSelector.AssetCoinSelection result = assetSelector.select(wallet.calculateAllSpendCandidates(false), 1);
		assertEquals(Sets.newHashSet(tx3.getOutput(0)), result.gathered);
		CoinSelection result1 = bitcoinSelector.select(Coin.FIFTY_COINS, wallet.calculateAllSpendCandidates(false));
		assertEquals(Sets.newHashSet(tx4.getOutput(0)), result1.gathered);

		Transaction tx = new Transaction(wallet.getParams());
		Wallet.SendRequest request = Wallet.SendRequest.forTx(tx);
		request.shuffleOutputs = false;
		request.coinSelector = bitcoinSelector;
		AssetCoinSelector.addAssetOutput(tx, ScriptBuilder.createOutputScript(new ECKey().toAddress(params)), 2L);
		assetSelector.completeTx(wallet, request, 2L);
		assertEquals(tx3.getOutput(0).getOutPointFor(), tx.getInput(0).getOutpoint());
		assertEquals(tx4.getOutput(0).getOutPointFor(), tx.getInput(1).getOutpoint());
		assertEquals(Coin.valueOf(1000), tx.getFee());
		assertEquals(2L, SmartColors.removeMsbdropValuePadding(tx.getOutput(0).getValue().getValue()));
		assertEquals(3L, SmartColors.removeMsbdropValuePadding(tx.getOutput(1).getValue().getValue()));

		receiveTransaction(tx);
		Map<ColorDefinition, Long> change = scanner.getNetAssetChange(tx, wallet, colorChain);
		assertEquals(1, change.size());
		assertEquals(-2L, (long)change.get(def));
	}

	private void receiveTransaction(Transaction tx) {
		scanner.receiveFromBlock(tx, FakeTxBuilder.createFakeBlock(blockStore, tx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet.receiveFromBlock(tx, FakeTxBuilder.createFakeBlock(blockStore, tx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
	}

	@Test
	public void testRestoreFromSeed() {
		DeterministicSeed existingSeed = wallet.getKeyChainSeed();
		DeterministicSeed seed = new DeterministicSeed(existingSeed.getMnemonicCode(), null, null, existingSeed.getCreationTimeSeconds());
		assertEquals(seed, existingSeed);
		ColorKeyChain colorChain1 = ColorKeyChain.builder()
				.seed(seed)
				.build();
		DeterministicKeyChain chain =
				DeterministicKeyChain.builder()
						.seed(colorChain1.getSeed())
						.build();
		KeyChainGroup group = new KeyChainGroup(params);
		group.addAndActivateHDChain(colorChain1);
		group.addAndActivateHDChain(chain);
		Wallet wallet1 = new Wallet(params, group);
		DeterministicKey colorKey1 = colorChain1.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
		assertEquals(colorKey, colorKey1);
	}
}