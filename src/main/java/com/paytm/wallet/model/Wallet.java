package com.paytm.wallet.model;

import java.util.UUID;

public record Wallet(UUID id, UUID userId, long balancePaise) { }
