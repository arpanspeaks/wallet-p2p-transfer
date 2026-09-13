package com.paytm.wallet.repository;

import com.paytm.wallet.api.dto.TransferRequest;
import com.paytm.wallet.model.Transfer;
import com.paytm.wallet.model.entity.TransferEntity;
import com.paytm.wallet.util.EntryLogger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {
    private final JdbcTemplate jdbc;
    private final JpaTransferRepository transfers;
    private final EntryLogger entryLogger;

    public TransferRepository(JdbcTemplate jdbc, JpaTransferRepository transfers, EntryLogger entryLogger) {
        this.jdbc = jdbc;
        this.transfers = transfers;
        this.entryLogger = entryLogger;
    }

    public Optional<UUID> claim(UUID transferId, TransferRequest request) {
        entryLogger.log(getClass(), "claim", Map.of("transfer_id", transferId, "from", request.from(), "to", request.to(), "amount_paise", request.amount_paise()));
        return jdbc.query("INSERT INTO transfers(id, from_wallet_id, to_wallet_id, amount_paise, idempotency_key, status) VALUES (?, ?, ?, ?, ?, 'PENDING') ON CONFLICT (idempotency_key) DO NOTHING RETURNING id",
                resultSet -> resultSet.next() ? Optional.of(resultSet.getObject(1, UUID.class)) : Optional.empty(), transferId,
                request.from(), request.to(), request.amount_paise(), request.idempotency_key());
    }

    public Optional<Transfer> findByIdempotencyKey(String key) {
        entryLogger.log(getClass(), "findByIdempotencyKey", Map.of("idempotency_key_present", !key.isBlank()));
        return transfers.findByIdempotencyKey(key).map(this::map);
    }

    public Optional<Transfer> findById(UUID id) {
        entryLogger.log(getClass(), "findById", Map.of("transfer_id", id));
        return transfers.findById(id).map(this::map);
    }

    public void lockWallets(UUID firstWalletId, UUID secondWalletId) {
        entryLogger.log(getClass(), "lockWallets", Map.of("first_wallet_id", firstWalletId, "second_wallet_id", secondWalletId));
        List<UUID> ordered = jdbc.query("SELECT id FROM wallets WHERE id IN (?, ?) ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), firstWalletId, secondWalletId);
        if (ordered.size() != 2) {
            throw new IllegalStateException("wallet disappeared during transfer");
        }
        for (UUID walletId : ordered) {
            jdbc.queryForObject("SELECT id FROM wallets WHERE id = ? FOR UPDATE", UUID.class, walletId);
        }
    }

    public int conditionalDebit(UUID id, long amount) {
        entryLogger.log(getClass(), "conditionalDebit", Map.of("wallet_id", id, "amount_paise", amount));
        return jdbc.update("UPDATE wallets SET balance_paise = balance_paise - ?, updated_at = now() WHERE id = ? AND balance_paise >= ?", amount, id, amount);
    }

    public void credit(UUID id, long amount) {
        entryLogger.log(getClass(), "credit", Map.of("wallet_id", id, "amount_paise", amount));
        jdbc.update("UPDATE wallets SET balance_paise = balance_paise + ?, updated_at = now() WHERE id = ?", amount, id);
    }

    public void complete(UUID id) {
        entryLogger.log(getClass(), "complete", Map.of("transfer_id", id));
        jdbc.update("UPDATE transfers SET status = 'COMPLETED', updated_at = now() WHERE id = ?", id);
    }

    public void decline(UUID id, String reason) {
        entryLogger.log(getClass(), "decline", Map.of("transfer_id", id, "reason", reason));
        jdbc.update("UPDATE transfers SET status = 'DECLINED', decline_reason = ?, updated_at = now() WHERE id = ?", reason, id);
    }

    private Transfer map(TransferEntity transfer) {
        return new Transfer(transfer.getId(), transfer.getFromWalletId(), transfer.getToWalletId(), transfer.getAmountPaise(),
                transfer.getIdempotencyKey(), transfer.getStatus(), transfer.getDeclineReason(), transfer.getCreatedAt());
    }
}
