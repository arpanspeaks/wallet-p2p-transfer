package com.paytm.wallet.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;
    @Column(name = "external_user_id", nullable = false, unique = true)
    private String externalUserId;

    protected UserEntity() { }

    public UUID getId() { return id; }
    public String getExternalUserId() { return externalUserId; }
}
