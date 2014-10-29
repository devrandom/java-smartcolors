package org.smartcolors;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class SmartColors {

	/**
	 * Remove MSB-Drop nValue padding.
	 *
	 * <p>Result is always positive and less than 2**62</p>
	 *
	 * @return unpadded value
	 */
	public static long removeMsbdropValuePadding(long pv) {
		// Check padding indicator bit
		if ((pv & 1) == 0)
			return (pv >> 1) & Long.MAX_VALUE; // MSB padding was not used, just drop indicator
		// Test against increasingly smaller msb_masks until we find the MSB set to 1
		for (int i = 63 ; i >= 0 ; i--) {
			long mask = 1L << i;
			if ((mask & pv) != 0)
				return (pv & (~mask)) >> 1;
		}
		// Degenerate case, no MSB even though indicated
		checkState((pv >> 1) == 0);
		return 0;
	}

	/**
	 * Add MSB-Drop padding to value to generate nValue above dust limit
	 *
	 * <p><b>Not consensus critical</b></p>
	 *
	 * @param v positive unpadded value, less than 2**62
	 * @param minimumNValue the minimum value of the result, positive and less than 2**63
	 * @return the padded value
	 */
	public static long addMsbdropValuePadding(long v, long minimumNValue) {
		checkArgument(v >= 0);
		checkArgument(v < (1L << 62));
		checkArgument(minimumNValue >= 0);

		v = v << 1;
		if (v >= minimumNValue)
			return v; // No padding needed
		int i = 0;
		while ((1L << i) < (v | 1))
			i++;
		while (((1L << i) | v | 1) < minimumNValue)
			i++;
		return (1L << i) | v | 1;
	}

	public static Script makeOpReturnScript() {
		ScriptBuilder ret = new ScriptBuilder();
		ret.op(ScriptOpCodes.OP_RETURN);
		ret.data(ColorProof.SMART_ASSET_MARKER.getBytes());
		return ret.build();
	}

	public static final int ASSET_ADDRESS_VERSION = 63;
	public static final int ASSET_ADDRESS_VERSION_TESTNET = 81;
	public static final int[] ACCEPTABLE_VERSIONS = new int[]{ASSET_ADDRESS_VERSION};
	public static final int[] ACCEPTABLE_VERSIONS_TESTNET = new int[]{ASSET_ADDRESS_VERSION_TESTNET};
	public final static NetworkParameters ASSET_PARAMETERS = new MainNetParams() {
		@Override
		public int getAddressHeader() {
			return ASSET_ADDRESS_VERSION;
		}

		@Override
		public int[] getAcceptableAddressCodes() {
			return ACCEPTABLE_VERSIONS;
		}
	};

	public final static NetworkParameters ASSET_PARAMETERS_TESTNET = new MainNetParams() {
		@Override
		public int getAddressHeader() {
			return ASSET_ADDRESS_VERSION_TESTNET;
		}

		@Override
		public int[] getAcceptableAddressCodes() {
			return ACCEPTABLE_VERSIONS_TESTNET;
		}
	};

	public static NetworkParameters getAssetParameters(boolean isProd) {
		return isProd ? ASSET_PARAMETERS : ASSET_PARAMETERS_TESTNET;
	}

	public static Address toAssetAddress(Address address, boolean isProd) {
		return new Address(getAssetParameters(isProd), address.getHash160());
	}

	public static Address fromAssetAddress(Address address, NetworkParameters params) {
		checkState(address.getParameters() == getAssetParameters(params.equals(NetworkParameters.fromID(NetworkParameters.ID_MAINNET))));
		return new Address(params, address.getHash160());
	}
}
