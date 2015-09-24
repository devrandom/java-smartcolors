package org.smartcolors;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.wallet.KeyChainGroup;

/**
 * Created by devrandom on 2015-07-13.
 */
public class SmartWallet extends Wallet {
    public SmartWallet(NetworkParameters params, KeyChainGroup group) {
        super(params, group);
    }

    public SmartWallet(NetworkParameters params) {
        super(params);
    }

    public void lock() {
        lock.lock();
        keychainLock.lock();
    }

    public void unlock() {
        keychainLock.unlock();
        lock.unlock();
    }

    public void lockKeychain() {
        keychainLock.lock();
    }

    public void unlockKeychain() {
        keychainLock.unlock();
    }

    public KeyChainGroup getKeychain() {
        return keychain;
    }

    @Override
    public void saveNow() {
        super.saveNow();
    }

    @Override
    protected void saveLater() {
        super.saveLater();
    }
}
