package br.com.betufu.identity.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Access(AccessType.FIELD)
@Table(
        name = "betufu_profile",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_betufu_profile_identity_id",
                columnNames = {"identity_id"}))
public class BetufuProfile {

    @Id
    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;

    @Column(name = "identity_id", nullable = false, updatable = false)
    private UUID identityId;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Column(name = "favorite_organization_id")
    private UUID favoriteOrganizationId;

    @Column(
            name = "nickname_updated_at",
            columnDefinition = "timestamptz(6)")
    private Instant nicknameUpdatedAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamptz(6)")
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            columnDefinition = "timestamptz(6)")
    private Instant updatedAt;

    protected BetufuProfile() {}

    public UUID getProfileId() {
        return profileId;
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public String getNickname() {
        return nickname;
    }

    public UUID getFavoriteOrganizationId() {
        return favoriteOrganizationId;
    }

    public Instant getNicknameUpdatedAt() {
        return nicknameUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void changeNickname(String newNickname, Instant changedAt) {
        this.nickname = Objects.requireNonNull(newNickname, "newNickname must not be null");
        Instant effectiveChange = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.nicknameUpdatedAt = effectiveChange;
        this.updatedAt = effectiveChange;
    }

    void changeFavoriteOrganization(UUID organizationId, Instant changedAt) {
        this.favoriteOrganizationId = organizationId;
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }
}
