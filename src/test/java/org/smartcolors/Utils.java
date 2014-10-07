package org.smartcolors;

import java.math.BigInteger;

public class Utils {
	public static long parseBinary(String s) {
		BigInteger v = new BigInteger(s, 2);
		if (v.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
			v = v.add(BigInteger.valueOf(Long.MIN_VALUE).multiply(BigInteger.valueOf(2)));
		return v.longValue();
	}
}
