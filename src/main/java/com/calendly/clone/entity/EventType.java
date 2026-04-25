package com.calendly.clone.entity;

import com.calendly.clone.entity.enums.MeetingType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A meeting template created by a host.
 *
 * Examples:
 *   "30-min intro call"  → duration=30, type=ONE_ON_ONE
 *   "Team stand-up"      → duration=15, type=GROUP
 *   "Sales demo"         → duration=60, type=ROUND_ROBIN
 *
 * Relationships:
 *  - Many EventTypes belong to one User (owner)    (@ManyToOne)
 *  - Many EventTypes belong to one Profile         (@ManyToOne)
 *  - One EventType generates many Bookings         (@OneToMany)
 */
@Entity
@Table(name = "event_types", uniqueConstraints = {
        // slug must be unique within a profile
        @UniqueConstraint(columnNames = {"profile_id", "slug"})
})
public class EventType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The individual user who created this event type.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * The profile (personal or team) this event type is published under.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String title;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    /**
     * URL-safe slug for this event type.
     * Full booking URL: calendly.com/{profileSlug}/{slug}
     */
    @NotBlank
    @Column(nullable = false, length = 80)
    private String slug;

    /**
     * Duration of the meeting in minutes.
     */
    @Min(5)
    @Column(nullable = false)
    private int durationMinutes = 30;

    /**
     * Buffer time added before each booking (minutes).
     */
    @Column(nullable = false)
    private int bufferBefore = 0;

    /**
     * Buffer time added after each booking (minutes).
     */
    @Column(nullable = false)
    private int bufferAfter = 0;

    /**
     * Type of meeting:
     *   ONE_ON_ONE   – one host, one guest
     *   GROUP        – one host, many guests in same slot
     *   ROUND_ROBIN  – one guest, assigned to one host from team pool
     *   COLLECTIVE   – one guest, ALL team hosts must be free
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingType meetingType = MeetingType.ONE_ON_ONE;

    /**
     * Max guests per slot (only applies when meetingType = GROUP).
     */
    @Column
    private Integer maxInvitees;

    /**
     * Video meeting link (Zoom, Google Meet, Teams, etc.) included in
     * confirmation emails. Optional — leave blank if not applicable.
     */
    @Column(length = 500)
    private String meetingLink;

    /**
     * Color shown on the host's booking page (hex, e.g. "#2c5fff").
     */
    @Column(length = 10)
    private String color = "#2c5fff";

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Relationships ──────────────────────────────────

    @OneToMany(mappedBy = "eventType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Booking> bookings = new ArrayList<>();

    // ── Constructors ───────────────────────────────────

    public EventType() {}

    public EventType(User owner, Profile profile, String title, String slug, int durationMinutes) {
        this.owner           = owner;
        this.profile         = profile;
        this.title           = title;
        this.slug            = slug;
        this.durationMinutes = durationMinutes;
    }

    // ── Getters & Setters ──────────────────────────────

    public Long getId()                                  { return id; }
    public User getOwner()                               { return owner; }
    public void setOwner(User owner)                     { this.owner = owner; }
    public Profile getProfile()                          { return profile; }
    public void setProfile(Profile profile)              { this.profile = profile; }
    public String getTitle()                             { return title; }
    public void setTitle(String title)                   { this.title = title; }
    public String getDescription()                       { return description; }
    public void setDescription(String desc)              { this.description = desc; }
    public String getSlug()                              { return slug; }
    public void setSlug(String slug)                     { this.slug = slug; }
    public int getDurationMinutes()                      { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes)  { this.durationMinutes = durationMinutes; }
    public int getBufferBefore()                         { return bufferBefore; }
    public void setBufferBefore(int bufferBefore)        { this.bufferBefore = bufferBefore; }
    public int getBufferAfter()                          { return bufferAfter; }
    public void setBufferAfter(int bufferAfter)          { this.bufferAfter = bufferAfter; }
    public MeetingType getMeetingType()                  { return meetingType; }
    public void setMeetingType(MeetingType meetingType)  { this.meetingType = meetingType; }
    public Integer getMaxInvitees()                      { return maxInvitees; }
    public void setMaxInvitees(Integer maxInvitees)      { this.maxInvitees = maxInvitees; }
    public String getMeetingLink()                        { return meetingLink; }
    public void setMeetingLink(String meetingLink)        { this.meetingLink = meetingLink; }
    public String getColor()                             { return color; }
    public void setColor(String color)                   { this.color = color; }
    public boolean isActive()                            { return active; }
    public void setActive(boolean active)                { this.active = active; }
    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public LocalDateTime getUpdatedAt()                  { return updatedAt; }
    public List<Booking> getBookings()                   { return bookings; }
}
