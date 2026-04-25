package com.calendly.clone.repository;

import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.TeamMember;
import com.calendly.clone.entity.User;
import com.calendly.clone.entity.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /** Find a specific user's membership in a specific profile */
    Optional<TeamMember> findByProfileAndUser(Profile profile, User user);

    /** All active members of a team profile */
    List<TeamMember> findByProfileAndStatus(Profile profile, MemberStatus status);

    /** Check if a user is already a member of a profile */
    boolean existsByProfileAndUser(Profile profile, User user);

    /** All team profiles a user belongs to */
    List<TeamMember> findByUser(User user);
}