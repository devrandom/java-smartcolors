package org.smartcolors;

import com.google.common.util.concurrent.ListenableFuture;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.smartcolors.core.ColorDefinition;

import java.util.Map;
import java.util.Set;

/**
 * Created by devrandom on 2014-Nov-23.
 */
public class ClientColorScanner extends AbstractColorScanner {
	public ClientColorScanner(NetworkParameters params) {
		super(params);
	}

	@Override
	public void addDefinition(ColorDefinition definition) throws SPVColorScanner.ColorDefinitionExists, SPVColorScanner.ColorDefinitionOutdated {

	}

	@Override
	protected ColorTrack makeTrack(ColorDefinition definition) {
		return null;
	}

	@Override
	public Map<ColorDefinition, Long> getNetAssetChange(Transaction tx, Wallet wallet, ColorKeyChain chain) {
		return null;
	}

	@Override
	public Map<ColorDefinition, Long> getBalances(Wallet wallet, ColorKeyChain colorKeyChain) {
		return null;
	}

	@Override
	public ListenableFuture<Transaction> getTransactionWithKnownAssets(Transaction tx, Wallet wallet, ColorKeyChain chain) {
		return null;
	}

	@Override
	public Set<ColorDefinition> getDefinitions() {
		return null;
	}

	@Override
	public boolean removeDefinition(ColorDefinition def) throws Exception {
		return false;
	}

	@Override
	public void reset() {

	}
}
