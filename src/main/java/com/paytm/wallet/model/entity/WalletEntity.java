package com.paytm.wallet.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class WalletEntity {
    @Id
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    protected WalletEntity() { }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public long getBalancePaise() { return balancePaise; }
}
