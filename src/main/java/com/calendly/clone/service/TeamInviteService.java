package com.calendly.clone.service;

import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.TeamInvite;
import com.calendly.clone.entity.TeamMember;
import com.calendly.clone.entity.User;
import com.calendly.clone.entity.enums.InviteStatus;
import com.calendly.clone.entity.enums.MemberRole;
import com.calendly.clone.entity.enums.MemberStatus;
import com.calendly.clone.repository.TeamInviteRepository;
import com.calendly.clone.repository.TeamMemberRepository;
import com.calendly.clone.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TeamInviteService
 *
 * Handles the full lifecycle of a team invite:
 *   1. Admin sends invite  → creates TeamInvite row, fires email
 *   2. Invitee clicks link → validates token, creates TeamMember row
 *   3. Admin revokes       → marks invite REVOKED
 *
 * Dependencies:
 *   - TeamInviteRepository  (Spring Data JPA)
 *   - TeamMemberRepository  (Spring Data JPA)
 *   - UserRepository        (Spring Data JPA)
 *   - EmailService          (JavaMailSender + Thymeleaf)
 *   - @Transactional        (spring-tx, via spring-boot-starter-data-jpa)
 */
@Service
public class TeamInviteService {

    private final TeamInviteRepository inviteRepo;
    private final TeamMemberRepository memberRepo;
    private final UserRepository       userRepo;
    private final EmailService         emailService;

    @Value("${app.invite.expiry-days:7}")
    private int expiryDays;

    public TeamInviteService(TeamInviteRepository inviteRepo,
                             TeamMemberRepository memberRepo,
                             UserRepository userRepo,
                             EmailService emailService) {
        this.inviteRepo   = inviteRepo;
        this.memberRepo   = memberRepo;
        this.userRepo     = userRepo;
        this.emailService = emailService;
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 1 — Admin sends an invite
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a TeamInvite row and fires the invite email.
     *
     * Guards:
     *  - Rejects if the email is already an ACTIVE member
     *  - Rejects if a PENDING invite already existsByOwner for this email
     *
     * @param profile the team profile sending the invite
     * @param email   the invitee's email address
     * @param role    the role they will receive on acceptance
     * @return the saved TeamInvite entity
     */
    @Transactional
    public TeamInvite sendInvite(Profile profile, String email, MemberRole role) {

        // Guard 1 — already a member?
        userRepo.findByEmail(email).ifPresent(user -> {
            if (memberRepo.existsByProfileAndUser(profile, user)) {
                throw new IllegalStateException(
                        email + " is already a member of this team.");
            }
        });

        // Guard 2 — already has a pending invite?
        if (inviteRepo.existsByProfileAndInvitedEmailAndStatus(
                profile, email, InviteStatus.PENDING)) {
            throw new IllegalStateException(
                    "A pending invite already existsByOwner for " + email);
        }

        // Generate secure token + expiry
        String        token  = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusDays(expiryDays);

        // Persist the invite row
        TeamInvite invite = new TeamInvite(profile, email, role, token, expiry);
        inviteRepo.save(invite);

        // Fire the email asynchronously (won't block this thread)
        emailService.sendTeamInviteEmail(invite);

        return invite;
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 2 — Invitee clicks the link and accepts
    // ─────────────────────────────────────────────────────────────

    /**
     * Validates the token and creates the TeamMember row.
     *
     * Called by TeamInviteController after the invitee
     * has registered or logged in.
     *
     * @param token the UUID token from the email link
     * @param user  the authenticated User who is accepting
     * @return the newly created TeamMember entity
     */
    @Transactional
    public TeamMember acceptInvite(String token, User user) {

        // 1. Find the invite row
        TeamInvite invite = inviteRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invite not found — the link may be invalid."));

        // 2. Check it is still valid (status=PENDING + not expired)
        if (!invite.isValid()) {
            throw new IllegalStateException(
                    "This invite has expired or has already been used.");
        }

        // 3. Guard — is this user already a member?
        if (memberRepo.existsByProfileAndUser(invite.getProfile(), user)) {
            // Mark invite used anyway, then return existing membership
            invite.markAccepted();
            inviteRepo.save(invite);
            return memberRepo
                    .findByProfileAndUser(invite.getProfile(), user)
                    .orElseThrow();
        }

        // 4. Create the TeamMember row
        TeamMember member = new TeamMember(
                invite.getProfile(),
                user,
                invite.getIntendedRole()
        );
        memberRepo.save(member);

        // 5. Mark the invite as used
        invite.markAccepted();
        inviteRepo.save(invite);

        return member;
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 3 — Admin revokes a pending invite
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks a PENDING invite as REVOKED.
     * The email link will no longer work.
     *
     * @param inviteId  the id of the TeamInvite to revoke
     * @param requester the admin/owner requesting the revocation
     */
    @Transactional
    public void revokeInvite(Long inviteId, User requester) {

        TeamInvite invite = inviteRepo.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invite not found."));

        // Only revoke if it's still pending
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING invites can be revoked.");
        }

        invite.revoke();
        inviteRepo.save(invite);
    }

    // ─────────────────────────────────────────────────────────────
    // Queries for dashboard display
    // ─────────────────────────────────────────────────────────────

    /** All pending invites for a team (shown on the admin dashboard) */
    public List<TeamInvite> getPendingInvites(Profile profile) {
        return inviteRepo.findByProfileAndStatus(
                profile, InviteStatus.PENDING);
    }

    /** All active members of a team */
    public List<TeamMember> getActiveMembers(Profile profile) {
        return memberRepo.findByProfileAndStatus(
                profile, MemberStatus.ACTIVE);
    }

    // ─────────────────────────────────────────────────────────────
    // Remove a member
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks a TeamMember as REMOVED (soft delete — keeps the row
     * for audit history).
     */
    @Transactional
    public void removeMember(Long memberId) {
        TeamMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member not found."));
        member.setStatus(MemberStatus.REMOVED);
        memberRepo.save(member);
    }
}
