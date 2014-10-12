package org.smartcolors;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;

import java.io.IOException;
import java.io.OutputStream;

public class TxOutGenesisPoint extends GenesisPoint {
	public static final byte POINT_TYPE = 0x01;
	private TransactionOutPoint outPoint;

	public TxOutGenesisPoint(NetworkParameters params, TransactionOutPoint outPoint) {
		this.params = params;
		this.outPoint = outPoint;
	}

	public TxOutGenesisPoint() {
	}

	@Override
	public byte getType() {
		return POINT_TYPE;
	}

	@Override
	protected void parse() {
		int cursor = this.cursor;
		cursor++; // Skip type
		outPoint = new TransactionOutPoint(params, payload, cursor);
		cursor += outPoint.getMessageSize();
	}

	@Override
	public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
		super.bitcoinSerializeToStream(stream);
		outPoint.bitcoinSerialize(stream);
	}

	public TransactionOutPoint getOutPoint() {
		return outPoint;
	}

	@Override
	public int hashCode() {
		return outPoint.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TxOutGenesisPoint))
			return false;
		return ((TxOutGenesisPoint)obj).outPoint.equals(outPoint);
	}

	@Override
	public String toString() {
		return "[TxOutGenesisPoint: " + outPoint + "]";
	}

	@Override
	public int compareTo(GenesisPoint o) {
		if (!(o instanceof TxOutGenesisPoint)) {
			if (getType() > o.getType())
				return 1;
			else if (getType() < o.getType())
				return -1;
			return 0;
		}
		TxOutGenesisPoint p = (TxOutGenesisPoint) o;
		byte[] bytes = outPoint.getHash().getBytes();
		byte[] obytes = p.outPoint.getHash().getBytes();
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] > obytes[i])
				return 1;
			else if (bytes[i] < obytes[i])
				return -1;
		}
		if (outPoint.getIndex() > p.outPoint.getIndex())
			return 1;
		else if (outPoint.getIndex() < p.outPoint.getIndex())
			return -1;
		return 0;
	}
}