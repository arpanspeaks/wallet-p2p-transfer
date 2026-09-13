package com.paytm.wallet.service;

import com.paytm.wallet.api.dto.TransferRequest;
import com.paytm.wallet.model.Transfer;
import java.util.UUID;

public interface TransferService {
    Transfer create(TransferRequest request, String caller);
    Transfer get(UUID id, String caller);
}
