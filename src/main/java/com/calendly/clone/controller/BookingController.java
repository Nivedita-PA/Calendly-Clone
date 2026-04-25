package com.calendly.clone.controller;

import com.calendly.clone.dto.BookingForm;
import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.EventType;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.repository.EventTypeRepository;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.service.BookingService;
import com.calendly.clone.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BookingController
 *
 * Public routes — no login required:
 *   GET  /book/{slug}/{eventSlug}    — show the booking page
 *   POST /book/{slug}/{eventSlug}    — submit the booking form
 *   GET  /confirm/{bookingId}        — show confirmation page
 *   GET  /cancel/{guestToken}        — guest self-service cancel page
 *   POST /cancel/{guestToken}        — confirm the cancellation
 */
@Controller
public class BookingController {

    private final BookingService        bookingService;
    private final ProfileRepository     profileRepo;
    private final EventTypeRepository   eventTypeRepo;
    private final ContactService        contactService;

    public BookingController(BookingService bookingService,
                             ProfileRepository profileRepo,
                             EventTypeRepository eventTypeRepo, ContactService contactService) {
        this.bookingService  = bookingService;
        this.profileRepo     = profileRepo;
        this.eventTypeRepo   = eventTypeRepo;
        this.contactService = contactService;
    }

    // ─────────────────────────────────────────────────────────────
    // GET — Show profile landing page (lists all event types)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/book/{profileSlug}")
    public String showProfilePage(@PathVariable String profileSlug, Model model) {
        Profile profile = profileRepo.findByBookingPageSlug(profileSlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found"));

        List<EventType> eventTypes = eventTypeRepo.findByProfileAndActiveTrue(profile);

        model.addAttribute("profile",    profile);
        model.addAttribute("eventTypes", eventTypes);

        return "booking-profile"; // → templates/booking-profile.html
    }

    // ─────────────────────────────────────────────────────────────
    // GET — Show booking page with open slots
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/book/{profileSlug}/{eventSlug}")
    public String showBookingPage(@PathVariable String profileSlug,
                                  @PathVariable String eventSlug,
                                  Model model) {
        Profile profile = profileRepo.findByBookingPageSlug(profileSlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found"));

        EventType eventType = eventTypeRepo
                .findByProfileAndSlug(profile, eventSlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event type not found"));

        List<LocalDateTime> openSlots = bookingService.getOpenSlots(eventType);
        String hostTimezone = profile.getTimezone();

        model.addAttribute("profile",      profile);
        model.addAttribute("eventType",    eventType);
        model.addAttribute("openSlots",    openSlots);
        model.addAttribute("slotsJson",    toSlotsJson(openSlots, hostTimezone));
        model.addAttribute("hostTimezone", hostTimezone);
        model.addAttribute("bookingForm",  new BookingForm());
        return "booking"; // → templates/booking.html
    }

    // ─────────────────────────────────────────────────────────────
    // POST — Process the booking form
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/book/{profileSlug}/{eventSlug}")
    public String createBooking(@PathVariable String profileSlug,
                                @PathVariable String eventSlug,
                                @Valid @ModelAttribute BookingForm form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes flash) {

        Profile profile = profileRepo.findByBookingPageSlug(profileSlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND));

        EventType eventType = eventTypeRepo
                .findByProfileAndSlug(profile, eventSlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND));

        String hostTimezone = profile.getTimezone();

        // Validation errors — re-render form with error messages
        if (result.hasErrors()) {
            List<LocalDateTime> slots = bookingService.getOpenSlots(eventType);
            model.addAttribute("profile",      profile);
            model.addAttribute("eventType",    eventType);
            model.addAttribute("openSlots",    slots);
            model.addAttribute("slotsJson",    toSlotsJson(slots, hostTimezone));
            model.addAttribute("hostTimezone", hostTimezone);
            return "booking";
        }

        try {
            Booking booking = bookingService.createBooking(eventType, form);

            // POST → Redirect → GET  (prevents duplicate submit on refresh)
            return "redirect:/confirm/" + booking.getId();

        } catch (IllegalStateException e) {
            // Slot was taken between GET and POST (race condition)
            List<LocalDateTime> slots = bookingService.getOpenSlots(eventType);
            model.addAttribute("slotError",    e.getMessage());
            model.addAttribute("profile",      profile);
            model.addAttribute("eventType",    eventType);
            model.addAttribute("openSlots",    slots);
            model.addAttribute("slotsJson",    toSlotsJson(slots, hostTimezone));
            model.addAttribute("hostTimezone", hostTimezone);
            return "booking";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET — Confirmation page after successful booking
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/confirm/{bookingId}")
    public String confirmationPage(@PathVariable Long bookingId, Model model) {
        try {
            Booking booking = bookingService.getBookingById(bookingId);
            String hostTimezone = booking.getEventType().getProfile().getTimezone();
            ZoneId hostZone = resolveZone(hostTimezone);

            model.addAttribute("booking",       booking);
            model.addAttribute("hostTimezone",  hostTimezone);
            model.addAttribute("startUtcMs",    booking.getStartTime().atZone(hostZone).toInstant().toEpochMilli());
            model.addAttribute("endUtcMs",      booking.getEndTime().atZone(hostZone).toInstant().toEpochMilli());
            return "confirm";
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET/POST — Guest self-service cancel
    // ─────────────────────────────────────────────────────────────

    /** Shows the "are you sure?" cancel page */
    @GetMapping("/cancel/{guestToken}")
    public String cancelPage(@PathVariable String guestToken, Model model) {
        model.addAttribute("guestToken", guestToken);
        return "cancel-confirm"; // → templates/cancel-confirm.html
    }

    /** Processes the confirmed cancellation */
    @PostMapping("/cancel/{guestToken}")
    public String processCancellation(@PathVariable String guestToken,
                                      RedirectAttributes flash) {
        try {
            bookingService.cancelByGuest(guestToken);
            flash.addFlashAttribute("message",
                    "Your booking has been cancelled.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cancel/done";
    }

    @GetMapping("/cancel/done")
    public String cancelDone() {
        return "cancel-done"; // → templates/cancel-done.html
    }

    // ─────────────────────────────────────────────────────────────
    // GET — Show edit-booking form (guest self-service)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/edit/{guestToken}")
    public String showEditPage(@PathVariable String guestToken, Model model) {
        Booking booking;
        try {
            booking = bookingService.getBookingByGuestToken(guestToken);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (booking.getStatus() != com.calendly.clone.entity.enums.BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.GONE, "This booking can no longer be edited.");
        }

        // Pre-populate the form with current values
        BookingForm form = new BookingForm();
        form.setGuestName(booking.getGuestName());
        form.setGuestEmail(booking.getGuestEmail());
        form.setGuestNotes(booking.getGuestNotes());
        form.setStartTime(booking.getStartTime());
        form.setAdditionalGuests(
            booking.getAdditionalGuests().stream()
                .map(g -> {
                    BookingForm.GuestEntry e = new BookingForm.GuestEntry();
                    e.setName(g.getName());
                    e.setEmail(g.getEmail());
                    return e;
                }).collect(Collectors.toList())
        );

        // Available slots — include the current slot even though it's "taken" by this booking
        List<LocalDateTime> openSlots = new ArrayList<>(
                bookingService.getOpenSlots(booking.getEventType()));
        if (!openSlots.contains(booking.getStartTime())) {
            openSlots.add(booking.getStartTime());
            openSlots.sort(Comparator.naturalOrder());
        }

        Profile editProfile = booking.getEventType().getProfile();
        String hostTimezone = editProfile.getTimezone();

        model.addAttribute("booking",      booking);
        model.addAttribute("bookingForm",  form);
        model.addAttribute("openSlots",    openSlots);
        model.addAttribute("slotsJson",    toSlotsJson(openSlots, hostTimezone));
        model.addAttribute("hostTimezone", hostTimezone);
        model.addAttribute("profile",      editProfile);
        model.addAttribute("eventType",    booking.getEventType());

        return "edit-booking";
    }

    // ─────────────────────────────────────────────────────────────
    // POST — Process the edit-booking form
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/edit/{guestToken}")
    public String processEdit(@PathVariable String guestToken,
                              @Valid @ModelAttribute BookingForm form,
                              BindingResult result,
                              Model model) {

        Booking booking;
        try {
            booking = bookingService.getBookingByGuestToken(guestToken);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        Profile editProfile2 = booking.getEventType().getProfile();
        String hostTimezone2 = editProfile2.getTimezone();

        if (result.hasErrors()) {
            List<LocalDateTime> openSlots = new ArrayList<>(
                    bookingService.getOpenSlots(booking.getEventType()));
            if (!openSlots.contains(booking.getStartTime())) {
                openSlots.add(booking.getStartTime());
                openSlots.sort(Comparator.naturalOrder());
            }
            model.addAttribute("booking",      booking);
            model.addAttribute("openSlots",    openSlots);
            model.addAttribute("slotsJson",    toSlotsJson(openSlots, hostTimezone2));
            model.addAttribute("hostTimezone", hostTimezone2);
            model.addAttribute("profile",      editProfile2);
            model.addAttribute("eventType",    booking.getEventType());
            return "edit-booking";
        }

        try {
            bookingService.editBooking(guestToken, form);
            return "redirect:/confirm/" + booking.getId();
        } catch (IllegalStateException e) {
            List<LocalDateTime> openSlots = new ArrayList<>(
                    bookingService.getOpenSlots(booking.getEventType()));
            if (!openSlots.contains(booking.getStartTime())) {
                openSlots.add(booking.getStartTime());
                openSlots.sort(Comparator.naturalOrder());
            }
            model.addAttribute("slotError",    e.getMessage());
            model.addAttribute("booking",      booking);
            model.addAttribute("openSlots",    openSlots);
            model.addAttribute("slotsJson",    toSlotsJson(openSlots, hostTimezone2));
            model.addAttribute("hostTimezone", hostTimezone2);
            model.addAttribute("profile",      editProfile2);
            model.addAttribute("eventType",    booking.getEventType());
            return "edit-booking";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static final DateTimeFormatter SLOT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Builds a list of {value, utcMs} maps for each slot — consumed by inline JS in templates. */
    private List<Map<String, Object>> toSlotsJson(List<LocalDateTime> slots, String hostTz) {
        ZoneId zone = resolveZone(hostTz);
        return slots.stream().map(slot -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value",  slot.format(SLOT_FMT));
            m.put("utcMs",  slot.atZone(zone).toInstant().toEpochMilli());
            return m;
        }).collect(Collectors.toList());
    }

    private ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneId.of("UTC");
        try { return ZoneId.of(tz); } catch (Exception e) { return ZoneId.of("UTC"); }
    }
}
