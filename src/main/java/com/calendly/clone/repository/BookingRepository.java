package com.calendly.clone.repository;

import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.EventType;
import com.calendly.clone.entity.User;
import com.calendly.clone.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** All confirmed bookings for a given event type (used for slot computation) */
    List<Booking> findByEventTypeAndStatus(EventType eventType, BookingStatus status);
    /**
     * Race-condition guard — checks whether ANY confirmed booking
     * for this event type overlaps the proposed [start, end] window.
     *
     * A conflict existsByOwner when:
     *   existing.startTime < proposed.endTime
     *   AND existing.endTime > proposed.startTime
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.eventType = :eventType
          AND b.status    = 'CONFIRMED'
          AND b.startTime < :end
          AND b.endTime   > :start
    """)
    boolean existsConflict(
            @Param("eventType") EventType eventType,
            @Param("start")     LocalDateTime start,
            @Param("end")       LocalDateTime end
    );

    /** Guest self-service cancel/reschedule — lookup by guest token */
    Optional<Booking> findByGuestToken(String guestToken);

    /**
     * Same as existsConflict but skips the booking being edited
     * so a guest can keep the same slot during an update.
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.eventType = :eventType
          AND b.status    = 'CONFIRMED'
          AND b.id       != :excludeId
          AND b.startTime < :end
          AND b.endTime   > :start
    """)
    boolean existsConflictExcluding(
            @Param("eventType")  EventType eventType,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end,
            @Param("excludeId")  Long excludeId
    );

    /** All bookings assigned to a specific host */
    List<Booking> findByAssignedHostAndStatus(User host, BookingStatus status);

    /** All bookings (any status) assigned to a specific host */
    List<Booking> findByAssignedHost(User host);

    /**
     * All CONFIRMED bookings for a host within a time range.
     * Used by AvailabilityService to subtract booked slots.
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.assignedHost = :host
          AND b.status       = 'CONFIRMED'
          AND b.startTime   >= :from
          AND b.endTime     <= :to
    """)
    List<Booking> findConfirmedInRange(
            @Param("host") User host,
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );

    List<Booking> findByStatus(BookingStatus bookingStatus);
}
