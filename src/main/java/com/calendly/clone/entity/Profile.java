package com.calendly.clone.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the public-facing profile of a user OR a shared team workspace.
 *
 * A Profile is the booking page that guests visit:
 *   e.g.  calendly.com/{bookingPageSlug}
 *
 * Relationships:
 *  - One Profile belongs to one User     (@OneToOne  — owns the FK)
 *  - One Profile has many TeamMembers    (@OneToMany — team workspace members)
 *  - One Profile has many TeamInvites    (@OneToMany — pending invitations)
 *  - One Profile has many EventTypes     (@OneToMany — meeting types offered)
 */
@Entity
@Table(name = "profiles", uniqueConstraints = {
        @UniqueConstraint(columnNames = "booking_page_slug")
})
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The FK to users table.
     * Profile owns this side of the @OneToOne relationship.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String displayName;

    @Size(max = 500)
    @Column(length = 500)
    private String bio;

    @Column
    private String avatarUrl;

    /**
     * IANA timezone string, e.g. "Asia/Kolkata", "America/New_York".
     */
    @Column(nullable = false, length = 60)
    private String timezone = "UTC";

    /**
     * The unique slug used in the public booking URL.
     * e.g. calendly.com/john-doe
     */
    @NotBlank
    @Column(name = "booking_page_slug", nullable = false, unique = true, length = 80)
    private String bookingPageSlug;

    /**
     * Whether this profile is visible to the public.
     * Hosts can hide their page while setting it up.
     */
    @Column(nullable = false)
    private boolean isPublic = true;

    /**
     * True when this Profile represents a shared team workspace
     * (multiple users sharing one booking page / round-robin pool).
     */
    @Column(nullable = false)
    private boolean isTeam = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Relationships ──────────────────────────────────

    /**
     * All users who are members of this profile/team.
     * Only meaningful when isTeam = true.
     */
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TeamMember> members = new ArrayList<>();

    /**
     * All pending (and historical) invitations sent from this profile.
     */
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TeamInvite> invites = new ArrayList<>();

    /**
     * Event types (meeting templates) published under this profile.
     */
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventType> eventTypes = new ArrayList<>();

    // ── Constructors ───────────────────────────────────

    public Profile() {}

    public Profile(User user, String displayName, String bookingPageSlug) {
        this.user            = user;
        this.displayName     = displayName;
        this.bookingPageSlug = bookingPageSlug;
    }

    // ── Helper: add/remove members ─────────────────────

    public void addMember(TeamMember member) {
        members.add(member);
        member.setProfile(this);
    }

    public void removeMember(TeamMember member) {
        members.remove(member);
        member.setProfile(null);
    }

    // ── Getters & Setters ──────────────────────────────

    public Long getId()                              { return id; }
    public User getUser()                            { return user; }
    public void setUser(User user)                   { this.user = user; }
    public String getDisplayName()                   { return displayName; }
    public void setDisplayName(String displayName)   { this.displayName = displayName; }
    public String getBio()                           { return bio; }
    public void setBio(String bio)                   { this.bio = bio; }
    public String getAvatarUrl()                     { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl)       { this.avatarUrl = avatarUrl; }
    public String getTimezone()                      { return timezone; }
    public void setTimezone(String timezone)         { this.timezone = timezone; }
    public String getBookingPageSlug()               { return bookingPageSlug; }
    public void setBookingPageSlug(String slug)      { this.bookingPageSlug = slug; }
    public boolean isPublic()                        { return isPublic; }
    public void setPublic(boolean isPublic)          { this.isPublic = isPublic; }
    public boolean isTeam()                          { return isTeam; }
    public void setTeam(boolean isTeam)              { this.isTeam = isTeam; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public LocalDateTime getUpdatedAt()              { return updatedAt; }
    public List<TeamMember> getMembers()             { return members; }
    public List<TeamInvite> getInvites()             { return invites; }
    public List<EventType> getEventTypes()           { return eventTypes; }
}