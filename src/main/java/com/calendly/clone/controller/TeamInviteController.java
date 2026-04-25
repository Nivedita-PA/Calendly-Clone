package com.calendly.clone.controller;

import com.calendly.clone.dto.InviteForm;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.TeamMember;
import com.calendly.clone.entity.User;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.repository.UserRepository;
import com.calendly.clone.service.TeamInviteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * TeamInviteController
 *
 * Handles:
 *   POST /team/{profileId}/invite       — admin sends invite
 *   GET  /join?token=...                — invitee clicks email link
 *   POST /team/{profileId}/revoke/{id}  — admin revokes invite
 *   POST /team/{profileId}/remove/{id}  — admin removes member
 *
 * All invite-sending routes require authentication.
 * The /join route is PUBLIC (invitee may not be logged in yet).
 */
@Controller
public class TeamInviteController {

    private final TeamInviteService inviteService;
    private final UserRepository    userRepo;
    private final ProfileRepository profileRepo;

    public TeamInviteController(TeamInviteService inviteService,
                                UserRepository userRepo,
                                ProfileRepository profileRepo) {
        this.inviteService = inviteService;
        this.userRepo      = userRepo;
        this.profileRepo   = profileRepo;
    }

    // ─────────────────────────────────────────────────────────────
    // Admin sends an invite
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /team/{profileId}/invite
     *
     * Validates the form, calls TeamInviteService.sendInvite(),
     * which saves the row and fires the email asynchronously.
     */
    @PostMapping("/team/{profileId}/invite")
    public String sendInvite(@PathVariable Long profileId,
                             @Valid @ModelAttribute InviteForm form,
                             BindingResult result,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes flash) {

        if (result.hasErrors()) {
            flash.addFlashAttribute("inviteError",
                    "Please enter a valid email and role.");
            return "redirect:/team/" + profileId + "/members";
        }

        Profile profile = profileRepo.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        try {
            inviteService.sendInvite(profile, form.getEmail(), form.getRole());
            flash.addFlashAttribute("inviteSuccess",
                    "Invite sent to " + form.getEmail());

        } catch (IllegalStateException e) {
            flash.addFlashAttribute("inviteError", e.getMessage());
        }

        return "redirect:/team/" + profileId + "/members";
    }

    // ─────────────────────────────────────────────────────────────
    // Invitee clicks the email link
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /join?token=...
     *
     * PUBLIC route — no login required yet.
     *
     * If the user is not logged in → redirect to /register?token=...
     * If the user is logged in     → accept the invite immediately
     .*/
    @GetMapping("/join")
    public String joinPage(@RequestParam String token,
                           @AuthenticationPrincipal UserDetails principal,
                           Model model,
                           RedirectAttributes flash) {

        // Not logged in — send to register/login first, preserving the token
        if (principal == null) {
            return "redirect:/register?token=" + token;
        }

        // Logged in — accept right away
        User user = userRepo.findByEmail(principal.getUsername())
                .orElseThrow();

        try {
            TeamMember member = inviteService.acceptInvite(token, user);
            flash.addFlashAttribute("joinSuccess",
                    "Welcome to " + member.getProfile().getDisplayName() + "!");
            return "redirect:/dashboard";

        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            return "join-error"; // → templates/join-error.html
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Admin revokes a pending invite
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/team/{profileId}/revoke/{inviteId}")
    public String revokeInvite(@PathVariable Long profileId,
                               @PathVariable Long inviteId,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes flash) {
        User requester = userRepo.findByEmail(principal.getUsername())
                .orElseThrow();

        try {
            inviteService.revokeInvite(inviteId, requester);
            flash.addFlashAttribute("success", "Invite revoked.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/team/" + profileId + "/members";
    }

    // ─────────────────────────────────────────────────────────────
    // Admin removes an active member
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/team/{profileId}/remove/{memberId}")
    public String removeMember(@PathVariable Long profileId,
                               @PathVariable Long memberId,
                               RedirectAttributes flash) {
        try {
            inviteService.removeMember(memberId);
            flash.addFlashAttribute("success", "Member removed.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/team/" + profileId + "/members";
    }
}
