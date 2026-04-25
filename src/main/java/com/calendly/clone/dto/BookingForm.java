package com.calendly.clone.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookingForm {

    @NotBlank(message = "Name is required")
    private String guestName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email")
    private String guestEmail;

    @NotNull(message = "Please select a time slot")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    private String guestNotes;

    /** Extra attendees beyond the primary guest. */
    private List<GuestEntry> additionalGuests = new ArrayList<>();

    // ── Inner DTO for additional guests ───────────────────────────
    public static class GuestEntry {
        private String name;
        private String email;

        public String getName()            { return name; }
        public void setName(String name)   { this.name = name; }
        public String getEmail()           { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    // ── Getters & Setters ──────────────────────────────────────────
    public String getGuestName()                            { return guestName; }
    public void setGuestName(String guestName)              { this.guestName = guestName; }
    public String getGuestEmail()                           { return guestEmail; }
    public void setGuestEmail(String guestEmail)            { this.guestEmail = guestEmail; }
    public LocalDateTime getStartTime()                     { return startTime; }
    public void setStartTime(LocalDateTime startTime)       { this.startTime = startTime; }
    public String getGuestNotes()                           { return guestNotes; }
    public void setGuestNotes(String guestNotes)            { this.guestNotes = guestNotes; }
    public List<GuestEntry> getAdditionalGuests()           { return additionalGuests; }
    public void setAdditionalGuests(List<GuestEntry> g)     { this.additionalGuests = g; }
}
