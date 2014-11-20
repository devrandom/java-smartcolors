package org.smartcolors;

import com.google.common.io.Resources;

import org.bitcoinj.core.NetworkParameters;
import org.junit.Before;
import org.junit.Test;
import org.smartcolors.core.ColorProof;
import org.smartcolors.core.GenesisScriptMerbinnerTree;
import org.smartcolors.marshal.BytesDeserializer;

import java.io.InputStream;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by devrandom on 2014-Nov-19.
 */
public class ColorProofTest {
	private NetworkParameters params;

	@Before
	public void setUp() {
		params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
	}

	@Test
	public void scriptTree() throws Exception {
		GenesisScriptMerbinnerTree tree = new GenesisScriptMerbinnerTree();
		tree.deserialize(new BytesDeserializer(Utils.HEX.decode("02020002011976a914d8296c49ea7fe04040e3d954abc80a5629e06f0788ac0117a914b77c14cdc812ddc740899867b1d5ae639a82db26870117a914da1745e9b549bd0bfa1a569971c77eba30cd5a4b87")));
		assertEquals("95a254d62931a7546dae40cef57b11b4d89c3e1721b517a3b983fa8f3aec9cfd", tree.getHash().toString());
	}

	@Test
	public void genesis1() throws Exception {
		InputStream is = Resources.getResource("proofs/genesis/2709f98721fbe6d3f78b45364cc2745e29da71c6c479498d4c91792af5c5fa9e:0.scproof").openStream();
		ColorProof proof = ColorProof.deserializeFromFile(params, is);
		assertEquals("f8bcdc4311624b8a0dcf79a92a46d08c26d1409066bb888f32d25f5f400e138d", proof.getHash().toString());
	}

	@Test
	public void genesis2() throws Exception {
		InputStream is = Resources.getResource("proofs/genesis/1051584e9e19f740ddf32b7b3d30c274ae23f2ff2b706f1446e4f7815563c3fd:0.scproof").openStream();
		ColorProof proof = ColorProof.deserializeFromFile(params, is);
		assertEquals("bafedb7c365d58eac2cb83513c4a084235826f092913022699d208d4d972b3d6", proof.getHash().toString());
	}
}
