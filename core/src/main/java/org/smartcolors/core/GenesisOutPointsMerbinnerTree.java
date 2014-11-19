package org.smartcolors.core;

import com.google.common.collect.Sets;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashSerializer;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.Serializer;

import java.util.Set;

/**
 * Created by devrandom on 2014-Nov-18.
 */
public class GenesisOutPointsMerbinnerTree extends MerbinnerTree<TransactionOutPoint, Long> {
	private NetworkParameters params;

	public static class MyNode extends Node<TransactionOutPoint, Long> {
		public MyNode(TransactionOutPoint key, long value) {
			this.key = key;
			this.value = value;
		}

		public MyNode() {
		}

		@Override
		public void serializeKey(Serializer ser) {
			if (ser instanceof HashSerializer)
				ser.write(getKeyHash());
			else
				ser.write(key.bitcoinSerialize());
		}

		@Override
		public void serializeValue(Serializer ser) {
			ser.write(value);
		}

		@Override
		public long getSum() {
			return value;
		}

		@Override
		public byte[] getKeyHash() {
			return HashSerializer.calcHash(key.bitcoinSerialize(), Utils.HEX.decode("eac9aef052700336a94accea6a883e59"));
		}
	}

	@Override
	protected MerbinnerTree.Node deserializeNode(Deserializer des) {
		TransactionOutPoint key = new TransactionOutPoint(params, des.readBytes(36), 0);
		long value = des.readVaruint();
		return new MyNode(key, value);
	}

	@Override
	protected void serializeSum(Serializer ser, long sum) {
		ser.write(sum);
	}

	@Override
	public byte[] getHmacKey() {
		return Utils.HEX.decode("d8497e1258c3f8e747341cb361676cee");
	}

	public GenesisOutPointsMerbinnerTree(NetworkParameters params, Set<Node<TransactionOutPoint, Long>> nodes) {
		super(nodes);
		this.params = params;
	}

	public GenesisOutPointsMerbinnerTree(NetworkParameters params) {
		super(Sets.<Node<TransactionOutPoint, Long>>newHashSet());
		this.params = params;
	}
}
