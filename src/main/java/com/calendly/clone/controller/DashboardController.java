package com.calendly.clone.controller;

import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.EventType;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.TeamInvite;
import com.calendly.clone.entity.TeamMember;
import com.calendly.clone.entity.User;
import com.calendly.clone.entity.enums.BookingStatus;
import com.calendly.clone.repository.BookingRepository;
import com.calendly.clone.repository.EventTypeRepository;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.repository.UserRepository;
import com.calendly.clone.service.BookingService;
import com.calendly.clone.service.TeamInviteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class DashboardController {

    private final UserRepository     userRepo;
    private final ProfileRepository  profileRepo;
    private final EventTypeRepository eventTypeRepo;
    private final BookingService     bookingService;
    private final TeamInviteService  teamInviteService;

    public DashboardController(UserRepository userRepo,
                               ProfileRepository profileRepo,
                               EventTypeRepository eventTypeRepo,
                               BookingService bookingService,
                               TeamInviteService teamInviteService) {
        this.userRepo          = userRepo;
        this.profileRepo       = profileRepo;
        this.eventTypeRepo     = eventTypeRepo;
        this.bookingService    = bookingService;
        this.teamInviteService = teamInviteService;
    }

    // ── Main dashboard ───────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user       = resolveUser(principal);
        Profile profile = resolveProfile(user);

        List<EventType> eventTypes      = eventTypeRepo.findByProfileAndActiveTrue(profile);
        List<Booking>   upcoming        = bookingService.getUpcomingBookings(user);
        List<Booking>   allBookings     = bookingService.getAllBookings(user);

        model.addAttribute("user",         user);
        model.addAttribute("profile",      profile);
        model.addAttribute("eventTypes",   eventTypes);
        model.addAttribute("upcoming",     upcoming);
        model.addAttribute("allBookings",  allBookings);
        model.addAttribute("bookingLink",  "/book/" + profile.getBookingPageSlug());

        return "dashboard";
    }

    // ── Host cancel booking ──────────────────────────────────────────

    @PostMapping("/bookings/{bookingId}/cancel")
    public String cancelBooking(@PathVariable Long bookingId,
                                @RequestParam(required = false) String cancelReason,
                                @AuthenticationPrincipal UserDetails principal,
                                RedirectAttributes flash) {
        User host = resolveUser(principal);

        Booking booking = bookingService.getBookingById(bookingId);

        // Security: only the assigned host may cancel
        if (!booking.getAssignedHost().getId().equals(host.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            flash.addFlashAttribute("error", "Only confirmed bookings can be cancelled.");
            return "redirect:/dashboard";
        }

        try {
            bookingService.cancelByHost(bookingId, cancelReason);
            flash.addFlashAttribute("success",
                    "Booking with " + booking.getGuestName() + " has been cancelled.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/dashboard";
    }

    // ── Team members page ────────────────────────────────────────────

    @GetMapping("/team/{profileId}/members")
    public String teamMembers(@PathVariable Long profileId,
                              @AuthenticationPrincipal UserDetails principal,
                              Model model) {
        User user = resolveUser(principal);
        Profile profile = profileRepo.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<TeamMember> members        = teamInviteService.getActiveMembers(profile);
        List<TeamInvite> pendingInvites = teamInviteService.getPendingInvites(profile);

        model.addAttribute("user",           user);
        model.addAttribute("profile",        profile);
        model.addAttribute("members",        members);
        model.addAttribute("pendingInvites", pendingInvites);
        model.addAttribute("inviteForm",     new com.calendly.clone.dto.InviteForm());
        model.addAttribute("roles",          com.calendly.clone.entity.enums.MemberRole.values());

        return "team-members";
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private User resolveUser(UserDetails principal) {
        return userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Profile resolveProfile(User user) {
        return profileRepo.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Profile not found"));
    }
}
