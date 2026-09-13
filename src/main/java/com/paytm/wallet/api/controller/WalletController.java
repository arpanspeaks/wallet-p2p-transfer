package com.paytm.wallet.api.controller;

import com.paytm.wallet.api.dto.WalletResponse;
import com.paytm.wallet.security.CallerIdentity;
import com.paytm.wallet.service.WalletService;
import com.paytm.wallet.util.EntryLogger;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService service;
    private final EntryLogger entryLogger;

    public WalletController(WalletService service, EntryLogger entryLogger) {
        this.service = service;
        this.entryLogger = entryLogger;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> getOrCreate(HttpServletRequest request) {
        CallerIdentity caller = (CallerIdentity) request.getAttribute("caller");
        entryLogger.log(getClass(), "getOrCreate", Map.of("caller_fingerprint", entryLogger.callerFingerprint(caller.userId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(WalletResponse.from(service.getOrCreate(caller.userId())));
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id, HttpServletRequest request) {
        entryLogger.log(getClass(), "get", Map.of("wallet_id", id));
        CallerIdentity caller = (CallerIdentity) request.getAttribute("caller");
        return WalletResponse.from(service.get(id, caller.userId()));
    }
}
