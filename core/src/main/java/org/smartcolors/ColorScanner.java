package org.smartcolors;

import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Wallet;
import org.smartcolors.core.ColorDefinition;

import java.util.Map;
import java.util.Set;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public interface ColorScanner {
	void addDefinition(ColorDefinition definition) throws ColorScanner.ColorDefinitionExists, ColorScanner.ColorDefinitionOutdated;

	Map<ColorDefinition, Long> getNetAssetChange(Transaction tx, Wallet wallet, ColorKeyChain chain);

	boolean contains(TransactionOutPoint point);

	Map<ColorDefinition, Long> getBalances(Wallet wallet, ColorKeyChain colorKeyChain);

	ListenableFuture<Transaction> getTransactionWithKnownAssets(Transaction tx, Wallet wallet, ColorKeyChain chain);

	Set<ColorDefinition> getDefinitions();

	void stop();

	void addPending(Transaction t);

	void start(Wallet wallet);

	ColorDefinition getColorDefinitionByHash(HashCode hash);

	ColorTrack getColorTrackByHash(HashCode hash);

	ColorTrack getColorTrackByDefinition(ColorDefinition def);

	boolean removeDefinition(ColorDefinition def) throws Exception;

	void reset();


	ColorDefinition getUnknownDefinition();

	void setColorKeyChain(ColorKeyChain colorKeyChain);

	ColorDefinition getBitcoinDefinition();

	Map<Sha256Hash,Transaction> getPending();

	void lock();

	void unlock();

	Map<ColorDefinition,Long> getOutputValues(Transaction tx, Wallet wallet, ColorKeyChain colorChain);

	public static class ScanningException extends RuntimeException {
		public ScanningException(String reason) {
			super(reason);
		}
	}

	public class ColorDefinitionException extends Exception {
		public ColorDefinitionException(String s) {
			super(s);
		}
	}

	public class ColorDefinitionOutdated extends ColorDefinitionException {
		public ColorDefinitionOutdated() {
			super("Trying to replace an existing definition with an older one.");
		}
	}

	public class ColorDefinitionExists extends ColorDefinitionException {
		public ColorDefinitionExists() {
			super("Trying to replace an existing definition.");
		}
	}
}
