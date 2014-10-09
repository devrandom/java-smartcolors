package org.smartcolors;

import com.google.common.collect.Maps;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public abstract class GenesisPoint {
	private static Map<Byte, Class<? extends GenesisPoint>> registry = Maps.newTreeMap();
	protected byte[] payload;
	protected NetworkParameters params;

	protected GenesisPoint() {
	}

	public static void register(byte type, Class<? extends GenesisPoint> clazz) {
		registry.put(type, clazz);
	}

	static {
		register(TxOutGenesisPoint.POINT_TYPE, TxOutGenesisPoint.class);
		register(ScriptPubkeyGenesisPoint.POINT_TYPE, ScriptPubkeyGenesisPoint.class);
	}

	public abstract byte getType();

	public static GenesisPoint fromPayload(NetworkParameters params, byte[] payload) throws ProtocolException {
		if (payload.length < 1)
			throw new ProtocolException("too short");
		byte type = payload[0];
		if (!registry.containsKey(type))
			throw new ProtocolException("unknown GenesisPoint type " + type);
		try {
			GenesisPoint point = registry.get(type).newInstance();
			point.payload = payload;
			point.params = params;
			point.parse();
			return point;
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	protected abstract void parse();

	public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
		stream.write(getType());
	}

	public abstract byte[] getBloomFilterElement();
}
