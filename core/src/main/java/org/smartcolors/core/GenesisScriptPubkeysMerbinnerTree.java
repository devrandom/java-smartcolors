package org.smartcolors.core;

import com.google.common.collect.Maps;

import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.Serializer;

import java.util.Map;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public class GenesisScriptPubkeysMerbinnerTree extends MerbinnerTree<Script, Void> {
	@Override
	public void serializeKey(Serializer ser, Script key) {
		ser.write(key.getProgram());
	}

	@Override
	public void serializeValue(Serializer ser, Void value) {
	}

	@Override
	public long getSum(Void value) {
		return 0;
	}

	@Override
	public byte[] getKeyHash(Script key) {
		return HashSerializer.calcHash(key.getProgram(), Utils.HEX.decode("3b808252881682adf56f7cc5abc0cb3c"));
	}

	@Override
	protected void deserializeNode(Deserializer des) {
		Script key = new Script(des.readBytes());
		entries.put(key, null);
	}

	@Override
	protected void serializeSum(Serializer ser, long sum) {
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
