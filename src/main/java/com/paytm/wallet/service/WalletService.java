package com.paytm.wallet.service;

import com.paytm.wallet.model.Wallet;
import java.util.UUID;

public interface WalletService {
    Wallet getOrCreate(String externalUserId);
    Wallet get(UUID walletId, String externalUserId);
    boolean isOwnedBy(UUID walletId, String externalUserId);
}
