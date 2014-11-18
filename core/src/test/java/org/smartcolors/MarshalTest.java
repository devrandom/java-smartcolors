package org.smartcolors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.smartcolors.marshal.BytesDeserializer;
import org.smartcolors.marshal.BytesSerializer;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.Serializable;
import org.smartcolors.marshal.Serializer;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class MarshalTest {
	private ObjectMapper mapper;

	@Before
	public void setUp() {
		mapper = new ObjectMapper();
	}

	static class BoxedVaruint implements Serializable {
		public long value;
		private byte[] cachedHash;

		public BoxedVaruint(long value) {
			this.value = value;
		}
		public void serialize(Serializer ser) {
			ser.write(value);
		}

		@Override
		public byte[] getHash() {
			if (cachedHash != null)
				return cachedHash;
			HashSerializer serializer = new HashSerializer();
			serialize(serializer);
			cachedHash = HashSerializer.calcHash(serializer, getHmacKey());
			return cachedHash;
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("dd2617248e435da6db7c119c17cc19cd");
		}

		public static BoxedVaruint deserialize(Deserializer ser) {
			return new BoxedVaruint(ser.readVaruint());
		}
	}

	static class BoxedBytes implements Serializable {
		public byte[] bytes;
		private byte[] cachedHash;

		public BoxedBytes(byte[] bytes) {
			this.bytes = bytes;
		}

		public void serialize(Serializer serializer) {
			serializer.writeWithLength(bytes);
		}

		public void serializeFixed(Serializer ser) {
			ser.write(bytes);
		}

		@Override
		public byte[] getHash() {
			if (cachedHash != null)
				return cachedHash;
			HashSerializer serializer = new HashSerializer();
			serialize(serializer);
			cachedHash = HashSerializer.calcHash(serializer, getHmacKey());
			return cachedHash;
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("f690a4d282810e868a0d7d59578a6585");
		}

		public static BoxedBytes deserialize(Deserializer ser, Integer expectedLength) {
			if (expectedLength != null)
				return new BoxedBytes(ser.readBytes(expectedLength));
			else
				return new BoxedBytes(ser.readBytes());
		}
	}

	static class BoxedObj implements Serializable {
		public BoxedBytes buf;
		public BoxedVaruint i;
		private byte[] cachedHash;

		public BoxedObj(byte[] buf, long i) {
			this.buf = new BoxedBytes(buf);
			this.i = new BoxedVaruint(i);
		}

		public BoxedObj() {
		}

		public void serialize(Serializer ser) {
			ser.write(buf);
			ser.write(i);
		}

		public static BoxedObj deserialize(Deserializer des) {
			BoxedObj obj = new BoxedObj();
			obj.buf = BoxedBytes.deserialize(des, null);
			obj.i = BoxedVaruint.deserialize(des);
			return obj;
		}

		@Override
		public byte[] getHash() {
			if (cachedHash != null)
				return cachedHash;
			HashSerializer serializer = new HashSerializer();
			serialize(serializer);
			cachedHash = HashSerializer.calcHash(serializer, getHmacKey());
			return cachedHash;
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("296d566c10ebb4b92e8a7f6e909eb191");
		}
	}

	@Test
	public void testVaruint() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/valid_varuints.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> entry : items) {
			BytesSerializer serializer = new BytesSerializer();
			if (entry.size() == 1) continue; // comment
			byte[] expected = Utils.HEX.decode(entry.get(0));
			long value = Long.parseLong(entry.get(1));
			BoxedVaruint boxed = new BoxedVaruint(value);
			boxed.serialize(serializer);
			byte[] bytes = serializer.getBytes();
			assertArrayEquals(entry.get(0), expected, bytes);
			BytesDeserializer deserializer = new BytesDeserializer(bytes);
			assertEquals(value, BoxedVaruint.deserialize(deserializer).value);
		}
	}

	@Test
	public void testBytes() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/valid_bytes.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> entry : items) {
			BytesSerializer serializer = new BytesSerializer();
			if (entry.size() == 1) continue; // comment
			byte[] expectedBytes = Utils.HEX.decode(entry.get(0));
			byte[] value = Utils.HEX.decode(entry.get(1));
			Integer expectedLength = entry.get(2) == null ? null : Integer.valueOf(entry.get(2));
			BoxedBytes boxed = new BoxedBytes(value);
			if (expectedLength != null)
				boxed.serializeFixed(serializer);
			else
				boxed.serialize(serializer);
			byte[] bytes = serializer.getBytes();
			assertArrayEquals(entry.get(0), expectedBytes, bytes);
			BytesDeserializer deserializer = new BytesDeserializer(bytes);
			assertArrayEquals(value, BoxedBytes.deserialize(deserializer, expectedLength).bytes);
		}
		System.out.println(items);
	}

	@Test
	public void testObjs() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/valid_boxed_objs.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> entry : items) {
			BytesSerializer serializer = new BytesSerializer();
			if (entry.size() == 1) continue; // comment
			byte[] expectedBytes = Utils.HEX.decode(entry.get(0));
			byte[] buf = Utils.HEX.decode(entry.get(1));
			long i = Long.parseLong(entry.get(2));
			byte[] expectedHash = Utils.HEX.decode(entry.get(3));
			BoxedObj boxed = new BoxedObj(buf, i);
			serializer.write(boxed);
			byte[] bytes = serializer.getBytes();
			assertArrayEquals(entry.get(0), expectedBytes, bytes);
			BytesDeserializer deserializer = new BytesDeserializer(bytes);
			BoxedObj actual = BoxedObj.deserialize(deserializer);
			assertArrayEquals(actual.buf.bytes, buf);
			assertEquals(actual.i.value, i);
			serializer = new BytesSerializer();
			serializer.write(actual);
			byte[] roundTrip = serializer.getBytes();
			assertArrayEquals(expectedBytes, roundTrip);
			byte[] hash = boxed.getHash();
			assertArrayEquals(expectedHash, hash);
		}
	}
}
