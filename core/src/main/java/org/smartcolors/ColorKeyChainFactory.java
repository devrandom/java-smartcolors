package org.smartcolors;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DefaultKeyChainFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by android on 10/12/14.
 */
public class ColorKeyChainFactory extends DefaultKeyChainFactory {
	private final Callback callback;

	public interface Callback {
		public void onRestore(ColorKeyChain chain);
	}

	public ColorKeyChainFactory(Callback callback) {
		checkNotNull(callback);
		this.callback = callback;
	}

	@Override
	public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried) {
		List<Integer> path = firstSubKey.getDeterministicKey().getPathList();
		if (!path.isEmpty() && path.get(0).equals(ColorKeyChain.ASSET_PATH.get(0).i())) {
			checkArgument(!isMarried, "no multisig support yet");
			ColorKeyChain result = new ColorKeyChain(seed, crypter);
			callback.onRestore(result);
			return result;
		} else {
			return new DefaultKeyChainFactory().makeKeyChain(key, firstSubKey, seed, crypter, isMarried);
		}
	}

	@Override
	public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isFollowingKey, boolean isMarried) {
		List<Integer> path = firstSubKey.getDeterministicKey().getPathList();
		if (!path.isEmpty() && path.get(0).equals(ColorKeyChain.ASSET_PATH)) {
			ColorKeyChain result = new ColorKeyChain(accountKey, isFollowingKey);
			callback.onRestore(result);
			return result;
		} else {
			return new DefaultKeyChainFactory().makeWatchingKeyChain(key, firstSubKey, accountKey, isFollowingKey, isMarried);
		}
	}
}
