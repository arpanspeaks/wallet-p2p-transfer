package com.paytm.wallet.model;

import java.time.Instant;
import java.util.UUID;

public record Transfer(UUID id, UUID fromWalletId, UUID toWalletId, long amountPaise, String idempotencyKey,
                       String status, String declineReason, Instant createdAt) { }
