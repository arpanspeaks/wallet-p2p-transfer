package com.paytm.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record TransferRequest(@NotNull UUID from, @NotNull UUID to, @Positive long amount_paise,
                              @NotBlank String idempotency_key) { }
