package org.smartcolors.core;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.FileSerializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.HashableSerializable;
import org.smartcolors.marshal.MemoizedDeserializer;
import org.smartcolors.marshal.SerializationException;
import org.smartcolors.marshal.Serializer;

import java.io.InputStream;
import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public abstract class ColorProof extends HashableSerializable {
	public static final int VERSION = 1;
	protected ColorDefinition def;
	protected long quantity;

	private static Map<Integer, Class<? extends ColorProof>> registry = Maps.newTreeMap();
	protected NetworkParameters params;

	public static void register(int type, Class<? extends ColorProof> clazz) {
		registry.put(type, clazz);
	}

	static {
		register(GenesisOutPointColorProof.PROOF_TYPE, GenesisOutPointColorProof.class);
		register(GenesisScriptColorProof.PROOF_TYPE, GenesisScriptColorProof.class);
	}

	public static ColorProof deserialize(final NetworkParameters params, Deserializer des) throws SerializationException {
		int type = des.readVaruint();
		if (!registry.containsKey(type))
			throw new SerializationException("unknown proof type " + type);
		ColorProof inst;
		try {
			inst = registry.get(type).newInstance();
		} catch (InstantiationException e) {
			throw Throwables.propagate(e);
		} catch (IllegalAccessException e) {
			throw Throwables.propagate(e);
		}
		int version = des.readVaruint();
		if (version != VERSION)
			throw new SerializationException("unknown vesion " + version);
		inst.params = params;
		inst.def = des.readObject(new Deserializer.ObjectReader<ColorDefinition>() {
			@Override
			public ColorDefinition readObject(Deserializer des) throws SerializationException {
				return ColorDefinition.deserialize(params, des);
			}
		});
		inst.deserialize(des);
		return inst;
	}

	protected abstract void deserialize(Deserializer des) throws SerializationException;

	@Override
	public void serialize(Serializer ser) throws SerializationException {
		ser.write(getType());
		ser.write(VERSION);
		ser.write(def);
		if (ser instanceof HashSerializer)
			ser.write(quantity);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("b96dae8e52cb124d01804353736a8384");
	}

	abstract int getType();

	public static final byte[] FILE_MAGIC = Utils.HEX.decode("00536d617274636f6c6f727300f8acdc00436f6c6f7270726f6f6600cb93f2c5");

	public static ColorProof deserializeFromFile(final NetworkParameters params, InputStream is) throws SerializationException {
		MemoizedDeserializer des = new MemoizedDeserializer(is);

		FileSerializer fser = new FileSerializer() {
			@Override
			protected byte[] getMagic() {
				return FILE_MAGIC;
			}
		};
		fser.readHeader(des);
		ColorProof me = des.readObject(new Deserializer.ObjectReader<ColorProof>() {
			@Override
			public ColorProof readObject(Deserializer des) throws SerializationException {
				return deserialize(params, des);
			}
		});
		fser.verifyHash(des, me);
		return me;
	}
}
