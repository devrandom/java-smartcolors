package org.smartcolors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
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
		def = new ColorDefinition(Sets.<GenesisPoint>newTreeSet());
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
	public void deserialize() throws IOException {
		byte[] defBytes = Utils.HEX.decode("00000000000000000000000000000000000000000000000000000000000000000000000000000000010174b16bf3ce53c26c3bc7a42f06328b4776a616182478b7011fba181db0539fc500000000");
		ColorDefinition def = ColorDefinition.fromPayload(params, defBytes);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		def.bitcoinSerialize(bos);
		assertArrayEquals(defBytes, bos.toByteArray());
		assertEquals("0a20faec7138b5f312ed58fb86cb181fc3719c28f2a167f02064f2cd5a90ec3a", Utils.HEX.encode(def.getHash().getBytes()));
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
