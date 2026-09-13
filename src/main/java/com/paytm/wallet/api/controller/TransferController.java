package com.paytm.wallet.api.controller;

import com.paytm.wallet.api.dto.TransferRequest;
import com.paytm.wallet.api.dto.TransferResponse;
import com.paytm.wallet.model.Transfer;
import com.paytm.wallet.security.CallerIdentity;
import com.paytm.wallet.service.TransferService;
import com.paytm.wallet.util.EntryLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService service;
    private final EntryLogger entryLogger;

    public TransferController(TransferService service, EntryLogger entryLogger) {
        this.service = service;
        this.entryLogger = entryLogger;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request, HttpServletRequest http) {
        String caller = caller(http);
        entryLogger.log(getClass(), "create", Map.of("from_wallet_id", request.from(), "to_wallet_id", request.to(),
                "amount_paise", request.amount_paise(), "caller_fingerprint", entryLogger.callerFingerprint(caller)));
        Transfer transfer = service.create(request, caller);
        HttpStatus status = transfer.status().equals("COMPLETED") ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id, HttpServletRequest http) {
        String caller = caller(http);
        entryLogger.log(getClass(), "get", Map.of("transfer_id", id, "caller_fingerprint", entryLogger.callerFingerprint(caller)));
        return TransferResponse.from(service.get(id, caller));
    }

    private String caller(HttpServletRequest http) {
        return ((CallerIdentity) http.getAttribute("caller")).userId();
    }
}
