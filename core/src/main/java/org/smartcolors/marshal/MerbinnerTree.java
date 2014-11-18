package org.smartcolors.marshal;

import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public abstract class MerbinnerTree extends HashableSerializable {
	Set<Node> nodes;

	public MerbinnerTree(Set<Node> nodes) {
		this.nodes = nodes;
	}

	public MerbinnerTree() {
	}

	public static abstract class Node {
		public abstract void serializeKey(Serializer ser);

		public abstract void serializeValue(Serializer ser);

		public abstract long getSum();

		public abstract byte[] getKeyHash();
	}

	public Set<Node> getNodes() {
		return nodes;
	}

	@Override
	public void serialize(final Serializer ser) {
		serialize(ser, nodes, 0);
	}

	private long serialize(Serializer ser, Set<Node> nodes, int depth) {
		Iterator<Node> iter = nodes.iterator();
		if (nodes.isEmpty()) {
			ser.write(0);
			return 0;
		} else if (nodes.size() == 1) {
			ser.write(1);
			Node node = iter.next();
			node.serializeKey(ser);
			node.serializeValue(ser);
			return node.getSum();
		} else {
			ser.write(2);
			Set<Node> left = Sets.newHashSet();
			Set<Node> right = Sets.newHashSet();
			for (Node node : nodes) {
				byte[] keyHash = node.getKeyHash();
				boolean side = ((keyHash[depth / 8] >> (7 - (depth % 8))) & 1) == 1;
				if (side)
					left.add(node);
				else
					right.add(node);
			}
			long leftSum = subSerialize(ser, left, depth + 1);
			long rightSum = subSerialize(ser, right, depth + 1);
			return doSum(leftSum, rightSum);
		}
	}

	private long subSerialize(Serializer ser, Set<Node> nodes, int depth) {
		if (ser instanceof HashSerializer) {
			HashSerializer ser1 = new HashSerializer();
			long sum = serialize(ser1, nodes, depth);
			byte[] hash = HashSerializer.calcHash(ser1, getHmacKey());
			ser.write(hash);
			serializeSum(ser, sum);
			return sum;
		} else {
			return serialize(ser, nodes, depth);
		}
	}

	protected void serializeSum(Serializer ser, long sum) {
	}

	public void deserialize(Deserializer des) {
		long type = des.readVaruint();
		if (type == 0)
			; // nothing
		else if (type == 1) {
			Node node = deserializeNode(des);
			nodes.add(node);
		} else if (type == 2) {
			deserialize(des); // left
			deserialize(des); // right
		} else {
			throw new RuntimeException("unknown Merbinner node type " + type);
		}
	}

	protected abstract Node deserializeNode(Deserializer des);

	private long doSum(long leftSum, long rightSum) {
		return leftSum + rightSum;
	}
}
