package com.calendly.clone.entity;

import com.calendly.clone.entity.enums.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A confirmed appointment — one guest booked one slot
 * for a specific EventType.
 *
 * Relationships:
 *  - Many Bookings belong to one EventType  (@ManyToOne)
 *  - Many Bookings assigned to one User     (@ManyToOne → assignedHost)
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The event type this booking was made for.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    /**
     * For ROUND_ROBIN events: the specific team member assigned to this booking.
     * For ONE_ON_ONE: same as eventType.owner.
     * Null until assigned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_host_id")
    private User assignedHost;

    // ── Guest details (not a User — guests don't register) ──

    @NotBlank
    @Column(name = "guest_name", nullable = false, length = 100)
    private String guestName;

    @NotBlank
    @Email
    @Column(name = "guest_email", nullable = false, length = 100)
    private String guestEmail;

    /**
     * Any notes the guest provided when booking.
     */
    @Column(name = "guest_notes", length = 1000)
    private String guestNotes;

    // ── Timing ────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    // ── Status ────────────────────────────────────────

    /**
     * CONFIRMED  – active booking
     * CANCELLED  – cancelled by host or guest
     * RESCHEDULED – moved to a new time (old booking kept for audit)
     * COMPLETED  – meeting time has passed
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    /**
     * Reason provided when cancelling.
     */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /**
     * Unique token sent to guest so they can cancel/reschedule
     * without needing an account.
     */
    @Column(name = "guest_token", unique = true, length = 100)
    private String guestToken;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingGuest> additionalGuests = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // ── Constructors ───────────────────────────────────

    public Booking() {}

    public Booking(EventType eventType, User assignedHost,
                   String guestName, String guestEmail,
                   LocalDateTime startTime, LocalDateTime endTime,
                   String guestToken) {
        this.eventType    = eventType;
        this.assignedHost = assignedHost;
        this.guestName    = guestName;
        this.guestEmail   = guestEmail;
        this.startTime    = startTime;
        this.endTime      = endTime;
        this.guestToken   = guestToken;
        this.status       = BookingStatus.CONFIRMED;
    }

    // ── Getters & Setters ──────────────────────────────

    public Long getId()                                  { return id; }
    public EventType getEventType()                      { return eventType; }
    public void setEventType(EventType eventType)        { this.eventType = eventType; }
    public User getAssignedHost()                        { return assignedHost; }
    public void setAssignedHost(User assignedHost)       { this.assignedHost = assignedHost; }
    public String getGuestName()                         { return guestName; }
    public void setGuestName(String guestName)           { this.guestName = guestName; }
    public String getGuestEmail()                        { return guestEmail; }
    public void setGuestEmail(String guestEmail)         { this.guestEmail = guestEmail; }
    public String getGuestNotes()                        { return guestNotes; }
    public void setGuestNotes(String guestNotes)         { this.guestNotes = guestNotes; }
    public LocalDateTime getStartTime()                  { return startTime; }
    public void setStartTime(LocalDateTime startTime)    { this.startTime = startTime; }
    public LocalDateTime getEndTime()                    { return endTime; }
    public void setEndTime(LocalDateTime endTime)        { this.endTime = endTime; }
    public BookingStatus getStatus()                     { return status; }
    public void setStatus(BookingStatus status)          { this.status = status; }
    public String getCancelReason()                      { return cancelReason; }
    public void setCancelReason(String cancelReason)     { this.cancelReason = cancelReason; }
    public String getGuestToken()                        { return guestToken; }
    public void setGuestToken(String guestToken)         { this.guestToken = guestToken; }
    public List<BookingGuest> getAdditionalGuests()      { return additionalGuests; }
    public LocalDateTime getCreatedAt()                  { return createdAt; }
}
