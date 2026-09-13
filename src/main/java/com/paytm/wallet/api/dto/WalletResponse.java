package com.paytm.wallet.api.dto;

import com.paytm.wallet.model.Wallet;
import java.util.UUID;

public record WalletResponse(UUID wallet_id, long balance_paise) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.id(), wallet.balancePaise());
    }
}
