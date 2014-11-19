package org.smartcolors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;
import org.smartcolors.core.ColorDefinition;
import org.smartcolors.core.GenesisOutPointsMerbinnerTree;
import org.smartcolors.core.GenesisScriptPubkeysMerbinnerTree;
import org.smartcolors.core.SmartColors;
import org.smartcolors.marshal.BytesDeserializer;
import org.smartcolors.marshal.SerializationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.smartcolors.Utils.parseBinary;

public class ColorDefinitionTest {
	private ColorDefinition def;
	private ObjectMapper mapper;
	private NetworkParameters params;

	static class KernelTestItem {
		public String comment;
		public List<String> nseqs;
		public List<Long> outputs;
		public Long inputs[];
		public Long expected[];
	}

	@Before
	public void setUp() {
		def = new ColorDefinition(params, new GenesisOutPointsMerbinnerTree(params), new GenesisScriptPubkeysMerbinnerTree());
		mapper = new ObjectMapper();
		params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
	}

	@Test
	public void kernel() throws IOException {
		List<KernelTestItem> items =
				mapper.readValue(FixtureHelpers.fixture("kernel.json"),
						new TypeReference<List<KernelTestItem>>() {
						});
		for (KernelTestItem item : items) {
			checkKernel(item);
		}
	}

	@Test
	public void deserialize() throws IOException, SerializationException {
		byte[] defBytes = Utils.HEX.decode("0100ec746756751d8ac6e9345f9050e1565f013ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a0000000080e497d01200");
		BytesDeserializer des = new BytesDeserializer(defBytes);
		ColorDefinition def = ColorDefinition.deserialize(params, des);
		System.out.println(def.toStringFull());
		assertEquals(0, def.getBlockheight());
		long value = def.getOutPointGenesisPoints().get(new TransactionOutPoint(params, 0, new Sha256Hash("4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b")));
		assertEquals(5000000000L, value);

		assertEquals("989d170a0f0c3dfb8d5266d4e9d355583a6a3e100c0d08ff6dee81f43c33c150", Utils.HEX.encode(def.getHash()));
	}

	@Test
	public void json() throws IOException {
		ColorDefinition oil = mapper.readValue(FixtureHelpers.fixture("oil.json"), ColorDefinition.TYPE_REFERENCE);
		assertEquals("Oil", oil.getName());
		assertEquals("1357bf3a56cdb0af288805075c132d84510008f34a64043e9341ff9a1783b66b", oil.getHash().toString());
		String oilJson = mapper.writeValueAsString(oil);
		Map oilMap = mapper.readValue(FixtureHelpers.fixture("oil.json"), Map.class);
		Map reconstructedMap = mapper.readValue(oilJson, Map.class);
		assertEquals(oilMap, reconstructedMap);
	}

	private void checkKernel(KernelTestItem item) {
		Transaction tx = new Transaction(params);
		for (String nseq : item.nseqs) {
			TransactionInput input = new TransactionInput(params, tx, new byte[0]);
			input.setSequenceNumber(parseBinary(nseq));
			tx.addInput(input);
		}

		for (long outputAmount : item.outputs) {
			tx.addOutput(Coin.valueOf(SmartColors.addMsbdropValuePadding(outputAmount, 0)), new Script(new byte[0]));
		}

		Long[] colorOut = def.applyKernel(tx, item.inputs);
		assertArrayEquals(item.comment, item.expected, colorOut);
	}
}
