package com.onecore.sdk.core.system.accounts;

import android.accounts.Account;
import android.os.Bundle;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Virtual Account Manager.
 * Prevents target app from seeing the real system accounts.
 */
public class BAccountManagerService extends IBAccountManagerService.Stub {
    private static final BAccountManagerService sService = new BAccountManagerService();
    private final List<Account> mVirtualAccounts = new ArrayList<>();

    public static BAccountManagerService get() {
        return sService;
    }

    @Override
    public Account[] getAccounts(String type, String packageName) throws RemoteException {
        return mVirtualAccounts.toArray(new Account[0]);
    }

    @Override
    public boolean addAccountExplicitly(Account account, String password, Bundle extras) throws RemoteException {
        mVirtualAccounts.add(account);
        return true;
    }
}
