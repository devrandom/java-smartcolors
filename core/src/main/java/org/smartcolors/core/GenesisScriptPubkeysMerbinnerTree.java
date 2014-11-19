package org.smartcolors.core;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;

import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.smartcolors.marshal.BytesSerializer;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;
import org.smartcolors.marshal.SerializerHelper;

import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public class GenesisScriptPubkeysMerbinnerTree extends MerbinnerTree<Script, Void> {
	@Override
	public void serializeKey(Serializer ser, Script key) throws SerializationException {
		ser.write(key, new SerializerHelper<Script>() {
			@Override
			public void serialize(Serializer ser, Script obj) throws SerializationException {
				ser.writeWithLength(obj.getProgram());
			}

			@Override
			public HashCode getHash(Script obj) {
				return calcHash(obj);
			}
		});
	}

	@Override
	public void serializeValue(Serializer ser, Void value) {
	}

	@Override
	public long getSum(Void value) {
		return 0;
	}

	@Override
	public HashCode getKeyHash(Script key) {
		return calcHash(key);
	}

	private static HashCode calcHash(Script key) {
		BytesSerializer ser = new BytesSerializer();
		try {
			ser.writeWithLength(key.getProgram());
		} catch (SerializationException e) {
			Throwables.propagate(e);
		}
		return HashSerializer.calcHash(ser.getBytes(), Utils.HEX.decode("3b808252881682adf56f7cc5abc0cb3c"));
	}

	@Override
	protected void deserializeNode(Deserializer des) throws SerializationException {
		Script key = des.readObject(new Deserializer.ObjectReader<Script>() {
			@Override
			public Script readObject(Deserializer des) throws SerializationException {
				return new Script(des.readBytes());
			}
		});
		entries.put(key, null);
	}

	@Override
	protected void serializeSum(Serializer ser, long sum) throws SerializationException {
		ser.write(sum);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("d431b155684582c6e0eef8b38d62321e");
	}

	public GenesisScriptPubkeysMerbinnerTree(Map<Script, Void> nodes) {
		super(nodes);
	}

	public GenesisScriptPubkeysMerbinnerTree() {
		super(Maps.<Script, Void>newHashMap());
	}
}
