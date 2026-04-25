package com.calendly.clone.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * An additional attendee on a booking (beyond the primary guest).
 * Each row represents one extra person invited to the same meeting slot.
 */
@Entity
@Table(name = "booking_guests")
public class BookingGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @NotBlank
    @Column(name = "guest_name", nullable = false, length = 100)
    private String name;

    @NotBlank
    @Email
    @Column(name = "guest_email", nullable = false, length = 100)
    private String email;

    public BookingGuest() {}

    public BookingGuest(Booking booking, String name, String email) {
        this.booking = booking;
        this.name    = name;
        this.email   = email;
    }

    public Long getId()                     { return id; }
    public Booking getBooking()             { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public String getName()                 { return name; }
    public void setName(String name)        { this.name = name; }
    public String getEmail()                { return email; }
    public void setEmail(String email)      { this.email = email; }
}
