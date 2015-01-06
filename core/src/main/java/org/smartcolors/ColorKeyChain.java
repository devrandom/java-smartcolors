package org.smartcolors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.RedeemData;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by devrandom on 2014-10-12.
 */
public class ColorKeyChain extends DeterministicKeyChain {
	public static final int SMARTCOLOR_BASE58 = 1461; // "SC" in base58 represents 1461
	public static final ImmutableList<ChildNumber> ASSET_PATH = ImmutableList.of(new ChildNumber(SMARTCOLOR_BASE58, true), ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED);
	private LinkedHashMap<ByteString, RedeemData> redeemDataMap = new LinkedHashMap<ByteString, RedeemData>();

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

	public ColorKeyChain(KeyCrypter keyCrypter, KeyParameter aesKey, ColorKeyChain colorKeyChain) {
		super(keyCrypter, aesKey, colorKeyChain);
		maybeLookAheadScripts();
	}

	public static class Builder<T extends Builder<T>> extends DeterministicKeyChain.Builder<T> {
		protected Builder() {
		}

		public ColorKeyChain build() {
			checkState(random != null || entropy != null || seed != null, "Must provide either entropy or random");
			ColorKeyChain chain;
			if (random != null) {
				chain = new ColorKeyChain(random, bits, passphrase, Utils.currentTimeSeconds());
			} else if (entropy != null) {
				chain = new ColorKeyChain(entropy, passphrase, Utils.currentTimeSeconds());
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

	public boolean isOutputToMe(TransactionOutput output) {
		Script script = output.getScriptPubKey();
		if (script.isSentToRawPubKey()) {
			byte[] pubkey = script.getPubKey();
			return findKeyFromPubKey(pubkey) != null;
		} if (script.isPayToScriptHash()) {
			return findRedeemDataByScriptHash(ByteString.copyFrom(script.getPubKeyHash())) != null;
		} else if (script.isSentToAddress()) {
			byte[] pubkeyHash = script.getPubKeyHash();
			return findKeyFromPubHash(pubkeyHash) != null;
		} else {
			return false;
		}
	}

	@Override
	public DeterministicKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
		return new ColorKeyChain(keyCrypter, aesKey, this);
	}

	@Override
	protected DeterministicKeyChain makeDecryptedKeyChain(DeterministicSeed decSeed) {
		return new ColorKeyChain(decSeed);
	}

	@Override
	public int numBloomFilterEntries() {
		maybeLookAheadScripts();
		return getLeafKeys().size() * 2;
	}

	@Override
	public BloomFilter getFilter(int size, double falsePositiveRate, long tweak) {
		lock.lock();
		BloomFilter filter;
		try {
			filter = new BloomFilter(size, falsePositiveRate, tweak);
			for (Map.Entry<ByteString, RedeemData> entry : redeemDataMap.entrySet()) {
				filter.insert(entry.getKey().toByteArray());
				filter.insert(entry.getValue().redeemScript.getProgram());
			}
		} finally {
			lock.unlock();
		}
		return filter;
	}

	/** Get the redeem data for a key in this married chain */
	@Override
	public RedeemData getRedeemData(DeterministicKey key) {
		List<ECKey> keys = Lists.newArrayList((ECKey) key);
		Script redeemScript = ScriptBuilder.createOutputScript(keys.get(0));
		return RedeemData.of(keys, redeemScript);
	}

	@Override
	public Script freshOutputScript(KeyPurpose purpose) {
		DeterministicKey key = getKey(purpose);
		maybeLookAheadScripts();
		Script redeemScript = ScriptBuilder.createOutputScript(key);
		return ScriptBuilder.createP2SHOutputScript(redeemScript);
	}

	public Script currentOutputScript(KeyPurpose purpose) {
		DeterministicKey key = currentKey(purpose);
		Script redeemScript = ScriptBuilder.createOutputScript(key);
		return ScriptBuilder.createP2SHOutputScript(redeemScript);
	}

	public DeterministicKey currentKey(KeyPurpose purpose) {
		if (purpose == KeyPurpose.RECEIVE_FUNDS) {
			if (getIssuedExternalKeys() > 0) {
				DeterministicKey currentExternalKey = getKeyByPath(
						ImmutableList.<ChildNumber>builder().addAll(getAccountPath()).addAll(EXTERNAL_PATH).add(new ChildNumber(getIssuedExternalKeys() - 1)).build());
				return currentExternalKey;
			}
		} else if (purpose == KeyPurpose.CHANGE) {
			if (getIssuedInternalKeys() > 0) {
				DeterministicKey currentInternalKey = getKeyByPath(
						ImmutableList.<ChildNumber>builder().addAll(getAccountPath()).addAll(INTERNAL_PATH).add(new ChildNumber(getIssuedInternalKeys() - 1)).build());
				return currentInternalKey;
			}
		} else {
			throw new RuntimeException("unsupported key purpose");
		}
		DeterministicKey key = getKey(purpose);
		maybeLookAheadScripts();
		return key;
	}

	@Override
	public void maybeLookAheadScripts() {
		super.maybeLookAheadScripts();

		maybeLookAhead();
		int numLeafKeys = getLeafKeys().size();

		checkState(redeemDataMap.size() <= numLeafKeys, "Number of scripts is greater than number of leaf keys");
		if (redeemDataMap.size() == numLeafKeys)
			return;

		for (DeterministicKey key : getLeafKeys()) {
			RedeemData redeemData = getRedeemData(key);
			Script scriptPubKey = ScriptBuilder.createP2SHOutputScript(redeemData.redeemScript);
			redeemDataMap.put(ByteString.copyFrom(scriptPubKey.getPubKeyHash()), redeemData);
		}
	}

	@Nullable
	@Override
	public RedeemData findRedeemDataByScriptHash(ByteString bytes) {
		return redeemDataMap.get(bytes);
	}

}
