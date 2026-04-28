package com.onecore.sdk.core.system.accounts;

import android.accounts.Account;

interface IBAccountManagerService {
    Account[] getAccounts(String type, String packageName);
    boolean addAccountExplicitly(in Account account, String password, in android.os.Bundle extras);
}
