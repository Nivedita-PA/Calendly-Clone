package com.calendly.clone.entity;

import com.calendly.clone.entity.enums.InviteStatus;
import com.calendly.clone.entity.enums.MemberRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a pending invitation to join a team Profile.
 *
 * Flow:
 *   1. Admin creates TeamInvite (status = PENDING, token generated)
 *   2. Email is sent to invitedEmail with a link containing the token
 *   3. Invitee clicks link → registers or logs in
 *   4. TeamMember row is created, this invite → status = ACCEPTED
 *
 * The token approach lets us invite people who don't have
 * a User account yet — they sign up after clicking the link.
 *
 * Relationships:
 *  - Many TeamInvites belong to one Profile (@ManyToOne)
 */
@Entity
@Table(name = "team_invites")
public class TeamInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The team profile that sent this invite.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    /**
     * Email address the invite was sent to.
     * May or may not already have a User account.
     */
    @NotBlank
    @Email
    @Column(name = "invited_email", nullable = false, length = 100)
    private String invitedEmail;

    /**
     * Role the invitee will receive once they accept.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "intended_role", nullable = false, length = 20)
    private MemberRole intendedRole = MemberRole.MEMBER;

    /**
     * Secure random token embedded in the invite link.
     * e.g.  /join?token=550e8400-e29b-41d4-a716-446655440000
     */
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    /**
     * Lifecycle of the invite:
     *   PENDING  – email sent, not yet accepted
     *   ACCEPTED – invitee accepted and is now a TeamMember
     *   EXPIRED  – passed expiresAt without being accepted
     *   REVOKED  – manually cancelled by an admin
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InviteStatus status = InviteStatus.PENDING;

    /**
     * When this invite was created / email was sent.
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime sentAt;

    /**
     * Invite link expires after this time (default: 7 days).
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * When the invitee actually accepted (null until accepted).
     */
    @Column
    private LocalDateTime acceptedAt;

    // ── Constructors ───────────────────────────────────

    public TeamInvite() {}

    public TeamInvite(Profile profile, String invitedEmail, MemberRole intendedRole,
                      String token, LocalDateTime expiresAt) {
        this.profile      = profile;
        this.invitedEmail = invitedEmail;
        this.intendedRole = intendedRole;
        this.token        = token;
        this.expiresAt    = expiresAt;
        this.status       = InviteStatus.PENDING;
    }

    // ── Helper: check validity ─────────────────────────

    public boolean isValid() {
        return status == InviteStatus.PENDING
                && LocalDateTime.now().isBefore(expiresAt);
    }

    public void markAccepted() {
        this.status     = InviteStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public void markExpired() {
        this.status = InviteStatus.EXPIRED;
    }

    public void revoke() {
        this.status = InviteStatus.REVOKED;
    }

    // ── Getters & Setters ──────────────────────────────

    public Long getId()                              { return id; }
    public Profile getProfile()                      { return profile; }
    public void setProfile(Profile profile)          { this.profile = profile; }
    public String getInvitedEmail()                  { return invitedEmail; }
    public void setInvitedEmail(String email)        { this.invitedEmail = email; }
    public MemberRole getIntendedRole()              { return intendedRole; }
    public void setIntendedRole(MemberRole role)     { this.intendedRole = role; }
    public String getToken()                         { return token; }
    public void setToken(String token)               { this.token = token; }
    public InviteStatus getStatus()                  { return status; }
    public void setStatus(InviteStatus status)       { this.status = status; }
    public LocalDateTime getSentAt()                 { return sentAt; }
    public LocalDateTime getExpiresAt()              { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt){ this.expiresAt = expiresAt; }
    public LocalDateTime getAcceptedAt()             { return acceptedAt; }
}