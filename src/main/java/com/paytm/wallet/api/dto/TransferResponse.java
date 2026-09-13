package com.paytm.wallet.api.dto;

import com.paytm.wallet.model.Transfer;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID transfer_id, UUID from, UUID to, long amount_paise, String status,
                               String decline_reason, Instant created_at) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.id(), transfer.fromWalletId(), transfer.toWalletId(), transfer.amountPaise(),
                transfer.status(), transfer.declineReason(), transfer.createdAt());
    }
}
