package org.smartcolors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.FixtureHelpers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.smartcolors.Utils.parseBinary;

public class SmartColorsTest {
    private ObjectMapper mapper;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    public void testMsbdropUnpad() throws IOException {
        List<List<String>> items =
                mapper.readValue(FixtureHelpers.fixture("unpadding.json"),
                        new TypeReference<List<List<String>>>(){});
        for (List<String> item : items) {
            checkUnpad(item.get(0), item.get(1));
        }
    }

	private void checkUnpad(String paddedBinaryString, String expected) {
		assertEquals(paddedBinaryString + " -> " + expected, parseBinary(expected), SmartColors.removeMsbdropValuePadding(parseBinary(paddedBinaryString)));
	}

	@Test
	public void testMsbdropPad() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("padding.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> item : items) {
			checkPad(item.get(0), item.get(1), item.get(2));
		}
	}

	private void checkPad(String binaryString, String binaryMinimum, String expected) {
		assertEquals(binaryString + " -> " + expected + " min " + binaryMinimum,
				parseBinary(expected),
				SmartColors.addMsbdropValuePadding(parseBinary(binaryString), parseBinary(binaryMinimum)));
	}
}
