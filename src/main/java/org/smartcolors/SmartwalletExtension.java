package org.smartcolors;

import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletExtension;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by devrandom on 2014-Oct-17.
 */
public class SmartwalletExtension implements WalletExtension {
	protected ColorScanner scanner;

	@Override
	public String getWalletExtensionID() {
		return "org.smartcolors";
	}

	@Override
	public boolean isWalletExtensionMandatory() {
		return false;
	}

	@Override
	public byte[] serializeWalletExtension() {
		return new byte[0];
	}

	@Override
	public void deserializeWalletExtension(Wallet wallet, byte[] data) throws Exception {
	}

	public void setScanner(ColorScanner scanner) {
		checkNotNull(scanner);
		this.scanner = scanner;
	}

	public ColorScanner getScanner() {
		checkNotNull(scanner);
		return scanner;
	}
}
