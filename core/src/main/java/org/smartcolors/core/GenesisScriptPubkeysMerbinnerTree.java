package org.smartcolors.core;

import com.google.common.collect.Sets;

import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.Serializer;

import java.util.Set;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public class GenesisScriptPubkeysMerbinnerTree extends MerbinnerTree<Script, Void> {
	public static class MyNode extends Node<Script, Void> {
		public MyNode(Script key) {
			this.key = key;
		}

		public MyNode() {
		}

		@Override
		public void serializeKey(Serializer ser) {
			ser.write(key.getProgram());
		}

		@Override
		public void serializeValue(Serializer ser) {
		}

		@Override
		public long getSum() {
			return 0;
		}

		@Override
		public byte[] getKeyHash() {
			return HashSerializer.calcHash(key.getProgram(), Utils.HEX.decode("3b808252881682adf56f7cc5abc0cb3c"));
		}
	}

	@Override
	protected Node deserializeNode(Deserializer des) {
		Script key = new Script(des.readBytes());
		MyNode node = new MyNode(key);
		return node;
	}

	@Override
	protected void serializeSum(Serializer ser, long sum) {
		ser.write(sum);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("d431b155684582c6e0eef8b38d62321e");
	}

	public GenesisScriptPubkeysMerbinnerTree(Set<Node<Script, Void>> nodes) {
		super(nodes);
	}

	public GenesisScriptPubkeysMerbinnerTree() {
		super(Sets.<Node<Script, Void>>newHashSet());
	}
}
