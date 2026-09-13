package com.paytm.wallet.service.impl;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.paytm.wallet.api.dto.TransferRequest;
import com.paytm.wallet.api.exception.ApiException;
import com.paytm.wallet.model.Transfer;
import com.paytm.wallet.observability.TransferMetrics;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.TransferService;
import com.paytm.wallet.util.EntryLogger;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferServiceImpl implements TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);
    private final TransferRepository transfers;
    private final WalletRepository wallets;
    private final TransferMetrics metrics;
    private final EntryLogger entryLogger;

    public TransferServiceImpl(TransferRepository transfers, WalletRepository wallets, TransferMetrics metrics, EntryLogger entryLogger) {
        this.transfers = transfers;
        this.wallets = wallets;
        this.metrics = metrics;
        this.entryLogger = entryLogger;
    }

    @Override
    @Transactional
    public Transfer create(TransferRequest request, String caller) {
        entryLogger.log(getClass(), "create", Map.of("from_wallet_id", request.from(), "to_wallet_id", request.to(),
                "amount_paise", request.amount_paise(), "caller_fingerprint", entryLogger.callerFingerprint(caller)));
        if (request.from().equals(request.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "source and destination must differ");
        }
        if (wallets.findById(request.from()).isEmpty() || wallets.findById(request.to()).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "wallet not found");
        }
        if (!wallets.isOwnedBy(request.from(), caller)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "source wallet is not owned by caller");
        }
        transfers.lockWallets(request.from(), request.to());
        UUID id = UUID.randomUUID();
        if (transfers.claim(id, request).isEmpty()) {
            Transfer existing = transfers.findByIdempotencyKey(request.idempotency_key()).orElseThrow();
            if (!sameRequest(existing, request)) {
                throw new ApiException(HttpStatus.CONFLICT, "idempotency key has a different request body");
            }
            metrics.replayed().increment();
            log.info("transfer.idempotent_replay", keyValue("event", "transfer.idempotent_replay"),
                    keyValue("transfer_id", existing.id()), keyValue("amount_paise", existing.amountPaise()));
            return existing;
        }
        metrics.created().increment();
        log.info("transfer.created", keyValue("event", "transfer.created"), keyValue("transfer_id", id),
                keyValue("from_wallet_id", request.from()), keyValue("to_wallet_id", request.to()),
                keyValue("amount_paise", request.amount_paise()));
        if (transfers.conditionalDebit(request.from(), request.amount_paise()) == 0) {
            transfers.decline(id, "INSUFFICIENT_FUNDS");
            metrics.declined().increment();
            log.info("transfer.declined", keyValue("event", "transfer.declined"), keyValue("transfer_id", id),
                    keyValue("reason", "INSUFFICIENT_FUNDS"), keyValue("amount_paise", request.amount_paise()));
            return transfers.findById(id).orElseThrow();
        }
        log.info("transfer.debited", keyValue("event", "transfer.debited"), keyValue("transfer_id", id),
                keyValue("wallet_id", request.from()), keyValue("amount_paise", request.amount_paise()));
        transfers.credit(request.to(), request.amount_paise());
        log.info("transfer.credited", keyValue("event", "transfer.credited"), keyValue("transfer_id", id),
                keyValue("wallet_id", request.to()), keyValue("amount_paise", request.amount_paise()));
        transfers.complete(id);
        return transfers.findById(id).orElseThrow();
    }

    @Override
    public Transfer get(UUID id, String caller) {
        entryLogger.log(getClass(), "get", Map.of("transfer_id", id, "caller_fingerprint", entryLogger.callerFingerprint(caller)));
        Transfer transfer = transfers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "transfer not found"));
        if (!wallets.isOwnedBy(transfer.fromWalletId(), caller) && !wallets.isOwnedBy(transfer.toWalletId(), caller)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "transfer is not visible to caller");
        }
        return transfer;
    }

    private boolean sameRequest(Transfer transfer, TransferRequest request) {
        return transfer.fromWalletId().equals(request.from()) && transfer.toWalletId().equals(request.to())
                && transfer.amountPaise() == request.amount_paise();
    }
}
