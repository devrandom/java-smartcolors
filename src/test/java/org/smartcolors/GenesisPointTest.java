package org.smartcolors;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class GenesisPointTest {

	public static final NetworkParameters params = NetworkParameters.fromPmtProtocolID(NetworkParameters.PAYMENT_PROTOCOL_ID_MAINNET);

	@Test(expected = ProtocolException.class)
	public void testBadData1() {
		GenesisPoint.fromPayload(params, new byte[1], 0);
	}

	@Test(expected = ProtocolException.class)
	public void testBadData100() {
		GenesisPoint.fromPayload(params, new byte[100], 0);
	}

	@Test
	public void testTxOut() throws IOException {
		TxOutGenesisPoint point =
				new TxOutGenesisPoint(params, new TransactionOutPoint(params, 0x0fffffff, new Sha256Hash(new byte[32])));
		ByteOutputStream bos = new ByteOutputStream();
		point.bitcoinSerializeToStream(bos);
		// Python code uses 0xffffffff but bitcoinj doesn't handle roundtripping that
		assertEquals("010000000000000000000000000000000000000000000000000000000000000000ffffff0f",
				toHex(bos));
		TxOutGenesisPoint point1 =
				(TxOutGenesisPoint) GenesisPoint.fromPayload(params, Utils.HEX.decode("010000000000000000000000000000000000000000000000000000000000000000ffffff0f"), 0);
		assertEquals(point.getOutPoint(), point1.getOutPoint());
	}

	@Test
	public void testScriptPubkey() throws IOException {
		ScriptPubkeyGenesisPoint point =
				new ScriptPubkeyGenesisPoint(params, new Script(new byte[0]));
		ByteOutputStream bos = new ByteOutputStream();
		point.bitcoinSerializeToStream(bos);
		assertEquals("0200", toHex(bos));
		ScriptPubkeyGenesisPoint point1 =
				(ScriptPubkeyGenesisPoint) GenesisPoint.fromPayload(params, Utils.HEX.decode("0200"), 0);
		assertEquals(point.getScriptPubkey(), point1.getScriptPubkey());
	}

	private String toHex(ByteOutputStream bos) {
		return Utils.HEX.encode(bos.getBytes(), 0, bos.getCount());
	}
}
