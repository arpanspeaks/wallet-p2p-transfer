package com.paytm.wallet.repository;

import com.paytm.wallet.model.entity.TransferEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTransferRepository extends JpaRepository<TransferEntity, UUID> {
    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);
}
