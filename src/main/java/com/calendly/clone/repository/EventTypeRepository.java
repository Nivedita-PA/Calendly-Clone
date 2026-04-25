package com.calendly.clone.repository;

import com.calendly.clone.entity.EventType;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventTypeRepository extends JpaRepository<EventType, Long> {

    /**
     * Guest booking page lookup — profile slug + event slug
     */
    Optional<EventType> findByProfileAndSlug(Profile profile, String slug);

    /**
     * All active event types for a profile
     */
    List<EventType> findByProfileAndActiveTrue(Profile profile);

    /**
     * All event types created by a specific user
     */
    List<EventType> findByOwner(User owner);

    @Query("""
SELECT DISTINCT b.eventType
FROM Booking b
LEFT JOIN b.additionalGuests g
WHERE b.eventType.owner = :user
AND (
    b.guestEmail = :email
    OR g.email = :email
)
""")
    List<EventType> findEventTypesForContact(User user, String email);

}