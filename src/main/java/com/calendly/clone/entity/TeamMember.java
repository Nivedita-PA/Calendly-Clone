package com.calendly.clone.entity;

import com.calendly.clone.entity.enums.MemberRole;
import com.calendly.clone.entity.enums.MemberStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Join table between Profile and User for team membership.
 *
 * One row = one user's membership in one team profile.
 *
 * Relationships:
 *  - Many TeamMembers belong to one Profile  (@ManyToOne)
 *  - Many TeamMembers belong to one User     (@ManyToOne)
 *
 * Example:
 *   Profile: "Acme Sales Team"
 *   User: alice@acme.com  → OWNER
 *   User: bob@acme.com    → MEMBER
 *   User: carol@acme.com  → ADMIN
 *
 */
@Entity
@Table(name = "team_members", uniqueConstraints = {
        // A user can only have one membership per profile
        @UniqueConstraint(columnNames = {"profile_id", "user_id"})
})
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The team profile this membership belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    /**
     * The user who is a member of this profile.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Role within the team:
     *   OWNER  – full control, can delete team
     *   ADMIN  – can invite members, manage event types
     *   MEMBER – can manage only their own event types
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private MemberRole memberRole = MemberRole.MEMBER;

    /**
     * Membership lifecycle status:
     *   ACTIVE   – currently an active member
     *   REMOVED  – was removed by an admin
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    /**
     * When this user accepted the invite and became a member.
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime joinedAt;

    // ── Constructors ───────────────────────────────────

    public TeamMember() {}

    public TeamMember(Profile profile, User user, MemberRole memberRole) {
        this.profile    = profile;
        this.user       = user;
        this.memberRole = memberRole;
        this.status     = MemberStatus.ACTIVE;
    }

    // ── Getters & Setters ──────────────────────────────

    public Long getId()                          { return id; }
    public Profile getProfile()                  { return profile; }
    public void setProfile(Profile profile)      { this.profile = profile; }
    public User getUser()                        { return user; }
    public void setUser(User user)               { this.user = user; }
    public MemberRole getMemberRole()            { return memberRole; }
    public void setMemberRole(MemberRole role)   { this.memberRole = role; }
    public MemberStatus getStatus()              { return status; }
    public void setStatus(MemberStatus status)   { this.status = status; }
    public LocalDateTime getJoinedAt()           { return joinedAt; }
}