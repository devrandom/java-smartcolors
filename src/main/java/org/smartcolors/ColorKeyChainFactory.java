package org.smartcolors;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DefaultKeyChainFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by android on 10/12/14.
 */
public class ColorKeyChainFactory extends DefaultKeyChainFactory {
	@Override
	public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried) {
		DeterministicKeyChain result;
		List<Integer> path = firstSubKey.getDeterministicKey().getPathList();
		if (!path.isEmpty() && path.get(0).equals(ColorKeyChain.ASSET_PATH.get(0).i())) {
			checkArgument(!isMarried, "no multisig support yet");
			result = new ColorKeyChain(seed, crypter);
		} else {
			result = new DefaultKeyChainFactory().makeKeyChain(key, firstSubKey, seed, crypter, isMarried);
		}
		return result;
	}

	@Override
	public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isFollowingKey, boolean isMarried) {
		DeterministicKeyChain result;
		List<Integer> path = firstSubKey.getDeterministicKey().getPathList();
		if (!path.isEmpty() && path.get(0).equals(ColorKeyChain.ASSET_PATH)) {
			result = new ColorKeyChain(accountKey, isFollowingKey);
		} else {
			result = new DefaultKeyChainFactory().makeWatchingKeyChain(key, firstSubKey, accountKey, isFollowingKey, isMarried);
		}
		return result;
	}
}
