package org.smartcolors;

import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.ListenableFuture;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.smartcolors.core.ColorDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public interface ColorScanner {
	void addDefinition(ColorDefinition definition) throws SPVColorScanner.ColorDefinitionExists, SPVColorScanner.ColorDefinitionOutdated;

	Map<ColorDefinition, Long> getNetAssetChange(Transaction tx, Wallet wallet, ColorKeyChain chain);

	Map<ColorDefinition, Long> getBalances(Wallet wallet, ColorKeyChain colorKeyChain);

	ListenableFuture<Transaction> getTransactionWithKnownAssets(Transaction tx, Wallet wallet, ColorKeyChain chain);

	Set<ColorDefinition> getDefinitions();

	ColorDefinition getColorDefinitionByHash(Sha256Hash hash);

	ColorTrack getColorTrackByHash(HashCode hash);

	ColorTrack getColorTrackByDefinition(ColorDefinition def);

	boolean removeDefinition(ColorDefinition def) throws Exception;

	void reset();

	ColorDefinition getBitcoinDefinition();

	ColorDefinition getUnknownDefinition();

	void addAllPending(Wallet wallet, Collection<Transaction> txs);

	public static class ScanningException extends RuntimeException {
		public ScanningException(String reason) {
			super(reason);
		}
	}
}
