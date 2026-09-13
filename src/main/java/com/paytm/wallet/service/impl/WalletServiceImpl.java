package com.paytm.wallet.service.impl;

import com.paytm.wallet.api.exception.ApiException;
import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.WalletService;
import com.paytm.wallet.util.EntryLogger;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WalletServiceImpl implements WalletService {
    private final WalletRepository wallets;
    private final EntryLogger entryLogger;

    public WalletServiceImpl(WalletRepository wallets, EntryLogger entryLogger) {
        this.wallets = wallets;
        this.entryLogger = entryLogger;
    }

    @Override
    public Wallet getOrCreate(String externalUserId) {
        entryLogger.log(getClass(), "getOrCreate", Map.of("caller_fingerprint", entryLogger.callerFingerprint(externalUserId)));
        return wallets.getOrCreate(externalUserId);
    }

    @Override
    public Wallet get(UUID walletId, String externalUserId) {
        entryLogger.log(getClass(), "get", Map.of("wallet_id", walletId, "caller_fingerprint", entryLogger.callerFingerprint(externalUserId)));
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wallet not found"));
        if (!wallets.isOwnedBy(wallet.id(), externalUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "wallet is not owned by caller");
        }
        return wallet;
    }

    @Override
    public boolean isOwnedBy(UUID walletId, String externalUserId) {
        entryLogger.log(getClass(), "isOwnedBy", Map.of("wallet_id", walletId, "caller_fingerprint", entryLogger.callerFingerprint(externalUserId)));
        return wallets.isOwnedBy(walletId, externalUserId);
    }
}
