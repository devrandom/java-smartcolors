package org.smartcolors;

import com.google.common.collect.ImmutableList;

import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;

import java.security.SecureRandom;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by android on 10/12/14.
 */
public class ColorKeyChain extends DeterministicKeyChain {
	public static final ImmutableList<ChildNumber> ASSET_PATH = ImmutableList.of(new ChildNumber(1000, true), ChildNumber.ZERO);

	public ColorKeyChain(SecureRandom random, int bits, String passphrase, long seedCreationTimeSecs) {
		super(random, bits, passphrase, seedCreationTimeSecs);
	}

	public ColorKeyChain(byte[] entropy, String passphrase, long seedCreationTimeSecs) {
		super(entropy, passphrase, seedCreationTimeSecs);
	}

	public ColorKeyChain(DeterministicSeed seed) {
		super(seed);
	}

	public ColorKeyChain(DeterministicSeed seed, KeyCrypter crypter) {
		super(seed, crypter);
	}

	public ColorKeyChain(DeterministicKey accountKey, boolean isFollowingKey) {
		super(accountKey, isFollowingKey);
	}

	public static class Builder<T extends Builder<T>> extends DeterministicKeyChain.Builder<T> {
		protected Builder() {
		}

		public ColorKeyChain build() {
			checkState(random != null || entropy != null || seed != null, "Must provide either entropy or random");
			ColorKeyChain chain;
			if (random != null) {
				chain = new ColorKeyChain(random, bits, passphrase, seedCreationTimeSecs);
			} else if (entropy != null) {
				chain = new ColorKeyChain(entropy, passphrase, seedCreationTimeSecs);
			} else {
				chain = new ColorKeyChain(seed);
			}
			return chain;
		}
	}

	public static Builder<?> builder() {
		return new Builder();
	}

	@Override
	protected ImmutableList<ChildNumber> getAccountPath() {
		return ASSET_PATH;
	}
}
