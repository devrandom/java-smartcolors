package org.smartcolors;

import com.google.common.collect.Maps;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.testing.FakeTxBuilder;
import org.junit.Before;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.GenesisOutPointsMerbinnerTree;
import org.smartcolors.core.GenesisScriptMerbinnerTree;
import org.smartcolors.core.SmartColors;

import java.math.BigInteger;
import java.util.Map;

import static org.bitcoinj.core.Utils.HEX;

/**
 * Created by devrandom on 2014-Oct-21.
 */
public class ColorTest {
	public static final Script EMPTY_SCRIPT = new Script(new byte[0]);

	protected NetworkParameters params;
	protected ColorScanner scanner;
	protected Transaction genesisTx;
	protected TransactionOutPoint genesisOutPoint;
	protected MemoryBlockStore blockStore;
	protected StoredBlock genesisBlock;
	protected ColorDefinition def;
	protected Script opReturnScript;
	protected ColorKeyChain colorChain;
	protected Wallet wallet;
	protected BigInteger privkey;
	protected ECKey privkey1;

	@Before
	public void setUp() throws Exception {
		params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
		blockStore = new MemoryBlockStore(params);
		genesisTx = new Transaction(params);
		genesisTx.addInput(Sha256Hash.ZERO_HASH, 0, EMPTY_SCRIPT);
		opReturnScript = SmartColors.makeOpReturnScript();
		genesisTx.addOutput(Utils.makeAssetCoin(10), new Script(new byte[0]));
		genesisTx.addOutput(Coin.ZERO, opReturnScript);
		genesisBlock = FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock;
		genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		Map<TransactionOutPoint, Long> nodes = Maps.newHashMap();
		nodes.put(genesisOutPoint, 0L);
		GenesisOutPointsMerbinnerTree outPoints = new GenesisOutPointsMerbinnerTree(params, nodes);
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put("name", "widgets");
		def = new ColorDefinition(params, outPoints, new GenesisScriptMerbinnerTree(), metadata);
		scanner = new ColorScanner(params);
		scanner.addDefinition(def);
		colorChain = null;
		wallet = null;
		privkey = new BigInteger(1, HEX.decode("180cb41c7c600be951b5d3d0a7334acc7506173875834f7a6c4c786a28fcbb19"));
		privkey1 = new DumpedPrivateKey(TestNet3Params.get(), "92shANodC6Y4evT5kFzjNFQAdjqTtHAnDTLzqBBq4BbKUPyx6CD").getKey();
	}
}
