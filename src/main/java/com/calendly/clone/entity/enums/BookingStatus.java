package com.calendly.clone.entity.enums;

public enum BookingStatus {
    CONFIRMED,    // active booking
    CANCELLED,    // cancelled by host or guest
    RESCHEDULED,  // moved to new time (old record kept for audit trail)
    COMPLETED     // meeting time has passed
}