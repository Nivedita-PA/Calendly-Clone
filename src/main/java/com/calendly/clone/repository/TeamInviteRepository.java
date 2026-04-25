package com.calendly.clone.repository;

import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.TeamInvite;
import com.calendly.clone.entity.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    /** Find a single invite by its token (used when invitee clicks the link) */
    Optional<TeamInvite> findByToken(String token);

    /** All invites sent by a specific team profile */
    List<TeamInvite> findByProfile(Profile profile);

    /** Check if this email already has a PENDING invite for this profile */
    boolean existsByProfileAndInvitedEmailAndStatus(
            Profile profile, String invitedEmail, InviteStatus status);

    /** All pending invites for a profile (for admin dashboard display) */
    List<TeamInvite> findByProfileAndStatus(Profile profile, InviteStatus status);
}
