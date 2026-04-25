package com.calendly.clone.repository;

import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    /** Guest booking page lookup — by the public slug in the URL */
    Optional<Profile> findByBookingPageSlug(String bookingPageSlug);

    /** Find the profile owned by a specific user */
    Optional<Profile> findByUser(User user);

    /** Check if a slug is already taken before creating a profile */
    boolean existsByBookingPageSlug(String bookingPageSlug);
}
