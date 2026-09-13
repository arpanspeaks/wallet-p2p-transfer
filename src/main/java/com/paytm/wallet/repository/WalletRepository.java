package com.paytm.wallet.repository;

import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.entity.WalletEntity;
import com.paytm.wallet.util.EntryLogger;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {
    private final JdbcTemplate jdbc;
    private final JpaWalletRepository wallets;
    private final long initialBalance;
    private final EntryLogger entryLogger;

    public WalletRepository(JdbcTemplate jdbc, JpaWalletRepository wallets,
                            @Value("${wallet.initial-balance-paise}") long initialBalance, EntryLogger entryLogger) {
        this.jdbc = jdbc;
        this.wallets = wallets;
        this.initialBalance = initialBalance;
        this.entryLogger = entryLogger;
    }

    public Wallet getOrCreate(String externalUserId) {
        entryLogger.log(getClass(), "getOrCreate", Map.of("caller_fingerprint", entryLogger.callerFingerprint(externalUserId), "initial_balance_paise", initialBalance));
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, external_user_id) VALUES (?, ?) ON CONFLICT (external_user_id) DO NOTHING", userId, externalUserId);
        UUID resolvedUser = jdbc.queryForObject("SELECT id FROM users WHERE external_user_id = ?", UUID.class, externalUserId);
        int walletCreated = jdbc.update("INSERT INTO wallets(id, user_id, balance_paise) VALUES (?, ?, ?) ON CONFLICT (user_id) DO NOTHING",
                UUID.randomUUID(), resolvedUser, initialBalance);
        Wallet wallet = findByUserId(resolvedUser).orElseThrow();
        entryLogger.event("wallet.created_or_retrieved", Map.of("wallet_id", wallet.id(), "wallet_created", walletCreated == 1,
                "balance_paise", wallet.balancePaise(), "caller_fingerprint", entryLogger.callerFingerprint(externalUserId)));
        return wallet;
    }

    public Optional<Wallet> findById(UUID id) {
        entryLogger.log(getClass(), "findById", Map.of("wallet_id", id));
        return wallets.findById(id).map(this::map);
    }

    public boolean isOwnedBy(UUID walletId, String externalUserId) {
        entryLogger.log(getClass(), "isOwnedBy", Map.of("wallet_id", walletId, "caller_fingerprint", entryLogger.callerFingerprint(externalUserId)));
        return wallets.existsByIdAndUserExternalUserId(walletId, externalUserId);
    }

    private Optional<Wallet> findByUserId(UUID userId) {
        return wallets.findByUserId(userId).map(this::map);
    }

    private Wallet map(WalletEntity wallet) {
        return new Wallet(wallet.getId(), wallet.getUser().getId(), wallet.getBalancePaise());
    }
}
