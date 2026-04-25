package com.calendly.clone.entity;

import com.calendly.clone.entity.enums.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered user (host) in the system.
 *
 * Relationships:
 *  - One User has one Profile        (@OneToOne)
 *  - One User owns many EventTypes   (@OneToMany)
 *  - One User has many TeamMember rows (membership in team profiles)
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "username")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.ROLE_USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "owner")
    private List<Contact> contacts = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Relationships ──────────────────────────────────

    /**
     * One-to-one with Profile.
     * CascadeType.ALL: creating/deleting a User also creates/deletes their Profile.
     * mappedBy = "user": Profile owns the FK column (user_id).
     */
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    private Profile profile;

    /**
     * A User can own many EventTypes (their own booking pages).
     */
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventType> eventTypes = new ArrayList<>();

    /**
     * All team memberships this user belongs to (across all profiles/teams).
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TeamMember> teamMemberships = new ArrayList<>();

    // ── Constructors ───────────────────────────────────

    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email    = email;
        this.password = password;
    }

    // ── Getters & Setters ──────────────────────────────

    public Long getId()                          { return id; }
    public String getUsername()                  { return username; }
    public void setUsername(String username)     { this.username = username; }
    public String getEmail()                     { return email; }
    public void setEmail(String email)           { this.email = email; }
    public String getPassword()                  { return password; }
    public void setPassword(String password)     { this.password = password; }
    public UserRole getRole()                    { return role; }
    public void setRole(UserRole role)           { this.role = role; }
    public boolean isEnabled()                   { return enabled; }
    public void setEnabled(boolean enabled)      { this.enabled = enabled; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public Profile getProfile()                  { return profile; }
    public void setProfile(Profile profile)      { this.profile = profile; }
    public List<EventType> getEventTypes()       { return eventTypes; }
    public List<TeamMember> getTeamMemberships() { return teamMemberships; }
}