package org.smartcolors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import io.dropwizard.testing.FixtureHelpers;

import static org.junit.Assert.assertArrayEquals;
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
		def = new ColorDefinition();
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
