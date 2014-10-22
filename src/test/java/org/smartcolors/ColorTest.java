package org.smartcolors;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.testing.FakeTxBuilder;
import org.junit.Before;

import java.util.Map;
import java.util.SortedSet;

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

	@Before
	public void setUp() {
		params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
		blockStore = new MemoryBlockStore(params);
		genesisTx = new Transaction(params);
		genesisTx.addInput(Sha256Hash.ZERO_HASH, 0, EMPTY_SCRIPT);
		opReturnScript = SmartColors.makeOpReturnScript();
		genesisTx.addOutput(Utils.makeAssetCoin(10), new Script(new byte[0]));
		genesisTx.addOutput(Coin.ZERO, opReturnScript);
		genesisBlock = FakeTxBuilder.createFakeBlock(blockStore, genesisTx).storedBlock;
		genesisOutPoint = new TransactionOutPoint(params, 0, genesisTx);
		TxOutGenesisPoint genesis = new TxOutGenesisPoint(params, genesisOutPoint);
		SortedSet<GenesisPoint> points = Sets.newTreeSet();
		points.add(genesis);
		Map<String, String> metadata = Maps.newHashMap();
		metadata.put("name", "widgets");
		def = new ColorDefinition(points, metadata);
		scanner = new ColorScanner();
		scanner.addDefinition(def);
		colorChain = null;
		wallet = null;
	}
}
