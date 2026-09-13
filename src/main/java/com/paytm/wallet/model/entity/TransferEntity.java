package com.paytm.wallet.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class TransferEntity {
    @Id
    private UUID id;
    @Column(name = "from_wallet_id", nullable = false)
    private UUID fromWalletId;
    @Column(name = "to_wallet_id", nullable = false)
    private UUID toWalletId;
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(nullable = false)
    private String status;
    @Column(name = "decline_reason")
    private String declineReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransferEntity() { }

    public UUID getId() { return id; }
    public UUID getFromWalletId() { return fromWalletId; }
    public UUID getToWalletId() { return toWalletId; }
    public long getAmountPaise() { return amountPaise; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public String getDeclineReason() { return declineReason; }
    public Instant getCreatedAt() { return createdAt; }
}
