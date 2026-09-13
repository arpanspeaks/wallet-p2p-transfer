package com.paytm.wallet.repository;

import com.paytm.wallet.model.entity.WalletEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWalletRepository extends JpaRepository<WalletEntity, UUID> {
    Optional<WalletEntity> findByUserId(UUID userId);
    boolean existsByIdAndUserExternalUserId(UUID id, String externalUserId);
}
