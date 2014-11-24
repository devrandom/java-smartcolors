package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.KeyChainGroup;
import org.junit.Before;
import org.junit.Test;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.SmartColors;
import org.smartcolors.protos.Protos;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SPVColorScannerTest extends ColorTest {
	private SmartwalletExtension ext;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		ext = new SmartwalletExtension(params);
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
	public void testGetColors() {
		Set<ColorDefinition> colors = scanner.getDefinitions();
		assertEquals(3, colors.size());
		assertTrue(colors.contains(scanner.getBitcoinDefinition()));
		assertTrue(colors.contains(scanner.getUnknownDefinition()));
		assertTrue(colors.contains(def));
	}

	@Test
	public void testBloomFilter() throws Exception {
		// Genesis
		assertEquals(1, scanner.getBloomFilterElementCount());
		assertTrue(getBloomFilter().contains(org.bitcoinj.core.Utils.HEX.decode("534d415254415353")));
	}

	@Test
	public void testGetNetAssetChangeUnknown() {
		KeyChainGroup group = new KeyChainGroup(params);
		group.setLookaheadSize(20);
		group.setLookaheadThreshold(7);
		group.addAndActivateHDChain(colorChain);
		group.createAndActivateNewHDChain();
		wallet = new Wallet(params, group) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return true;
			}
		};
		Transaction tx2 = new Transaction(params);
		tx2.addInput(genesisTx.getOutput(0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(new ECKey()));
		tx2.addOutput(Coin.ZERO, opReturnScript);
		Map<ColorDefinition, Long> res = scanner.getNetAssetChange(tx2, wallet, colorChain);
		Map<ColorDefinition, Long> expected = Maps.newHashMap();
		expected.put(scanner.getUnknownDefinition(), 5L);
		assertEquals(expected, res);
	}

	@Test
	public void testGetNetAssetChange() throws SPVColorScanner.ColorDefinitionException {
		final ECKey myKey = ECKey.fromPrivate(privkey);
		final Map<Sha256Hash, Transaction> txs = Maps.newHashMap();
		scanner.receiveFromBlock(genesisTx, FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet = new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return Arrays.equals(pubkey, myKey.getPubKey());
			}

			@Nullable
			@Override
			public Transaction getTransaction(Sha256Hash hash) {
				return txs.get(hash);
			}
		};

		Transaction tx2 = new Transaction(params);
		tx2.addInput(SmartColors.makeAssetInput(tx2, genesisTx, 0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(myKey));
		tx2.addOutput(Coin.ZERO, opReturnScript);
		scanner.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		Map<ColorDefinition, Long> expected = Maps.newHashMap();
		Map<ColorDefinition, Long> res = scanner.getNetAssetChange(tx2, wallet, colorChain);
		expected.put(def, 5L);
		assertEquals(expected, res);


		Transaction tx3 = new Transaction(params);
		tx3.addInput(SmartColors.makeAssetInput(tx3, tx2, 0));
		tx3.addOutput(Utils.makeAssetCoin(2), ScriptBuilder.createOutputScript(myKey));
		tx3.addOutput(Utils.makeAssetCoin(3), ScriptBuilder.createOutputScript(privkey1));
		tx3.addOutput(Coin.ZERO, opReturnScript);
		scanner.receiveFromBlock(tx3, FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet.receiveFromBlock(tx3, FakeTxBuilder.createFakeBlock(blockStore, tx3).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);

		expected.clear();
		res = scanner.getNetAssetChange(tx3, wallet, colorChain);
		expected.put(def, -3L);
		assertEquals(expected, res);

		txs.put(genesisTx.getHash(), genesisTx);
		txs.put(tx2.getHash(), tx2);
		txs.put(tx3.getHash(), tx3);

		SPVColorScanner scanner1 = new SPVColorScanner(params);
		scanner1.addDefinition(def);
		Protos.ColorScanner proto = ext.serializeScanner(scanner);
		ext.deserializeScanner(params, proto, scanner1);
		assertEquals(scanner.getMapBlockTx(), scanner1.getMapBlockTx());
		assertEquals("9ba0c8df6d37c0dba260ee0510e68cb41d2d0b19396621757522e5cc270dddb8",
				scanner.getColorTrackByDefinition(def).getStateHash().toString());
	}

	@Test
	public void testGetTransactionWithUnknownAsset() throws ExecutionException, InterruptedException {
		final ECKey myKey = new ECKey();
		scanner.receiveFromBlock(genesisTx, FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet = new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return Arrays.equals(pubkey, myKey.getPubKey());
			}
		};

		Transaction tx2 = new Transaction(params);
		tx2.addInput(SmartColors.makeAssetInput(tx2, genesisTx, 0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(myKey));
		tx2.addOutput(Coin.ZERO, opReturnScript);
		wallet.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		ListenableFuture<Transaction> future = scanner.getTransactionWithKnownAssets(tx2, wallet, colorChain);
		//assertFalse(future.isDone());
		//scanner.receiveFromBlock(tx2, FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		// FIXME need a better test now that we apply the color kernel to unconfirmed
		assertTrue(future.isDone());
		assertEquals(tx2, future.get());
	}

	@Test
	public void testGetTransactionWithUnknownAssetFail() throws ExecutionException, InterruptedException {
		final ECKey myKey = new ECKey();
		scanner.receiveFromBlock(genesisTx, FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		wallet = new Wallet(params) {
			@Override
			public boolean isPubKeyMine(byte[] pubkey) {
				return Arrays.equals(pubkey, myKey.getPubKey());
			}
		};

		Transaction tx2a = new Transaction(params);
		tx2a.addOutput(Coin.ZERO, opReturnScript);
		Transaction tx2 = new Transaction(params);
		tx2.addInput(tx2a.getOutput(0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(myKey));
		tx2.addOutput(Coin.ZERO, opReturnScript);
		StoredBlock storedBlock = FakeTxBuilder.createFakeBlock(blockStore, tx2).storedBlock;
		wallet.receiveFromBlock(tx2, storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
		ListenableFuture<Transaction> future = scanner.getTransactionWithKnownAssets(tx2, wallet, colorChain);
		assertFalse(future.isDone());
		scanner.notifyNewBestBlock(storedBlock);
		assertTrue(future.isDone());
		try {
			future.get();
			fail();
		} catch (ExecutionException ex) {
			assertEquals(SPVColorScanner.ScanningException.class, ex.getCause().getClass());
		}
		// FIXME need better test
	}

	private BloomFilter getBloomFilter() {
		return scanner.getBloomFilter(10, 1e-12, (long) (Math.random() * Long.MAX_VALUE));
	}

	@Test
	public void testSerializePending() {
		Transaction tx2 = new Transaction(params);
		tx2.addInput(SmartColors.makeAssetInput(tx2, genesisTx, 0));
		tx2.addOutput(Utils.makeAssetCoin(5), ScriptBuilder.createOutputScript(privkey1));
		tx2.addOutput(Coin.ZERO, opReturnScript);
		scanner.addPending(tx2);
		Protos.ColorScanner scannerProto = ext.serializeScanner(scanner);
		SPVColorScanner scanner1 = new SPVColorScanner(params);
		ext.deserializeScanner(params, scannerProto, scanner1);
		assertEquals(tx2.getHash(), scanner1.getPending().keySet().iterator().next());
	}
}