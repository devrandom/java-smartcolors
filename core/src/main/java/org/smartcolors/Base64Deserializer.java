package org.smartcolors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.google.common.hash.HashCode;

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;

/**
 * Created by devrandom on 2014-Nov-26.
 */
public class Base64Deserializer extends FromStringDeserializer<byte[]> {
	public Base64Deserializer() {
		super(HashCode.class);
	}

	@Override
	protected byte[] _deserialize(String value, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		return Base64.decodeBase64(value);
	}
}
