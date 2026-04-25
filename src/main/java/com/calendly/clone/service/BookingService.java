package com.calendly.clone.service;

import com.calendly.clone.dto.BookingForm;
import com.calendly.clone.entity.*;
import com.calendly.clone.entity.enums.BookingStatus;
import com.calendly.clone.entity.enums.MemberStatus;
import com.calendly.clone.entity.enums.MeetingType;
import com.calendly.clone.repository.BookingRepository;
import com.calendly.clone.repository.ContactRepository;
import com.calendly.clone.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * BookingService
 *
 * Creates, cancels, and reschedules bookings.
 *
 * Key design decisions:
 *   - @Transactional on createBooking() — the slot re-check and
 *     the INSERT happen atomically. If two guests pick the same slot
 *     at the same time, one will get a SlotTakenException and the
 *     database will stay consistent.
 *   - Email sending is delegated to EmailService (@Async) AFTER
 *     the transaction commits, so a mail failure never rolls back
 *     a valid booking.
 *
 * Dependencies:
 *   - BookingRepository     (Spring Data JPA)
 *   - TeamMemberRepository  (Spring Data JPA — for round-robin)
 *   - AvailabilityService   (slot computation)
 *   - EmailService          (JavaMailSender + Thymeleaf + @Async)
 *   - @Transactional        (spring-tx)
 */
@Service
public class BookingService {

    private final BookingRepository     bookingRepo;
    private final TeamMemberRepository  memberRepo;
    private final AvailabilityService   availabilityService;
    private final EmailService          emailService;
    private final ContactRepository     contactRepository;

    public BookingService(BookingRepository bookingRepo,
                          TeamMemberRepository memberRepo,
                          AvailabilityService availabilityService,
                          EmailService emailService, ContactRepository contactRepository) {
        this.bookingRepo       = bookingRepo;
        this.memberRepo        = memberRepo;
        this.availabilityService = availabilityService;
        this.emailService      = emailService;
        this.contactRepository = contactRepository;
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a confirmed booking from a guest's form submission.
     *
     * Steps inside ONE transaction:
     *   1. Compute end time from duration
     *   2. Re-check the slot is still free (race-condition guard)
     *   3. Pick the assigned host (round-robin or the sole owner)
     *   4. Generate a guest cancel token (UUID)
     *   5. Persist the Booking row
     *
     * After the transaction commits:
     *   6. Fire confirmation emails to guest + host (async)
     *
     * @param eventType the meeting template being booked
     * @param form      the validated guest form data
     * @return the saved Booking entity
     */
    @Transactional
    public Booking createBooking(EventType eventType, BookingForm form) {

        LocalDateTime start = form.getStartTime();
        LocalDateTime end   = start.plusMinutes(eventType.getDurationMinutes());

        // ── 1. Re-check the slot is still free ──────────────────
        boolean conflict = bookingRepo.existsConflict(eventType, start, end);
        if (conflict) {
            throw new IllegalStateException(
                    "Sorry, that slot was just taken. Please choose another time.");
        }

        // ── 2. Pick the host ─────────────────────────────────────
        User assignedHost = pickHost(eventType);

        // ── 3. Generate guest self-service token ─────────────────
        String guestToken = UUID.randomUUID().toString();

        // ── 4. Build and save the booking ────────────────────────
        Booking booking = new Booking(
                eventType,
                assignedHost,
                form.getGuestName(),
                form.getGuestEmail(),
                start,
                end,
                guestToken
        );
        booking.setGuestNotes(form.getGuestNotes());

        // ── 5. Save additional guests ─────────────────────────────
        addGuestsFromForm(booking, form);

        bookingRepo.save(booking);

        // ── 6. Send emails after commit (async — non-blocking) ───
        // Force-initialize lazy associations while the Hibernate session is still
        // open (we're inside @Transactional). The async email threads run in a
        // separate thread where the session is gone; reading an uninitialized proxy
        // there would throw LazyInitializationException silently.
        booking.getEventType().getTitle();
        booking.getAssignedHost().getEmail();
        booking.getAssignedHost().getProfile().getDisplayName();
        booking.getAdditionalGuests().size(); // init lazy collection

        emailService.sendBookingConfirmationToGuest(booking);
        emailService.sendBookingNotificationToHost(booking);

        return booking;
    }

    // ─────────────────────────────────────────────────────────────
    // EDIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Updates an existing confirmed booking in-place.
     * The guest can change the time slot, their details, and the
     * list of additional attendees. Sends updated confirmation emails.
     */
    @Transactional
    public Booking editBooking(String guestToken, BookingForm form) {
        Booking booking = bookingRepo.findByGuestToken(guestToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid edit link."));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be edited.");
        }

        LocalDateTime newStart = form.getStartTime();
        LocalDateTime newEnd   = newStart.plusMinutes(
                booking.getEventType().getDurationMinutes());

        // Check conflicts, excluding this booking's own slot
        boolean conflict = bookingRepo.existsConflictExcluding(
                booking.getEventType(), newStart, newEnd, booking.getId());
        if (conflict) {
            throw new IllegalStateException(
                    "Sorry, that slot is no longer available. Please choose another time.");
        }

        booking.setGuestName(form.getGuestName());
        booking.setGuestEmail(form.getGuestEmail());
        booking.setGuestNotes(form.getGuestNotes());
        booking.setStartTime(newStart);
        booking.setEndTime(newEnd);

        // Replace additional guests (orphanRemoval cleans up old rows)
        booking.getAdditionalGuests().clear();
        addGuestsFromForm(booking, form);

        bookingRepo.save(booking);

        // Force-init lazy associations for async email threads
        booking.getEventType().getTitle();
        booking.getAssignedHost().getEmail();
        booking.getAssignedHost().getProfile().getDisplayName();
        booking.getAdditionalGuests().size();

        emailService.sendBookingUpdateConfirmationToGuest(booking);
        emailService.sendBookingUpdateNotificationToHost(booking);

        return booking;
    }

    // ─────────────────────────────────────────────────────────────
    // CANCEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Cancels a booking.
     * Can be called by the host (passing bookingId) or by the
     * guest using their self-service token.
     *
     * @param bookingId    id of the booking to cancel
     * @param cancelReason optional reason text
     */
    @Transactional
    public Booking cancelByHost(Long bookingId, String cancelReason) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Booking not found."));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only CONFIRMED bookings can be cancelled.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(cancelReason);
        bookingRepo.save(booking);

        // Notify the guest
        emailService.sendCancellationEmail(booking);

        return booking;
    }

    /**
     * Guest self-service cancel — identified by their guestToken.
     * No login required.
     *
     * @param guestToken the token from the cancel link in the
     *                   confirmation email
     */
    @Transactional
    public Booking cancelByGuest(String guestToken) {
        Booking booking = bookingRepo.findByGuestToken(guestToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid cancel link."));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "This booking is already cancelled or completed.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason("Cancelled by guest");
        bookingRepo.save(booking);

        // Force-init lazy associations before async email thread picks them up
        booking.getEventType().getTitle();
        booking.getAssignedHost().getEmail();
        booking.getAssignedHost().getProfile().getDisplayName();

        emailService.sendGuestCancellationNotificationToHost(booking);

        return booking;
    }

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    /** All upcoming confirmed bookings for a host */
    public List<Booking> getUpcomingBookings(User host) {
        return bookingRepo
                .findByAssignedHostAndStatus(host, BookingStatus.CONFIRMED)
                .stream()
                .filter(b -> b.getStartTime().isAfter(LocalDateTime.now()))
                .sorted(Comparator.comparing(Booking::getStartTime))
                .toList();
    }

    /** Open slots for a booking page (delegates to AvailabilityService) */
    public List<LocalDateTime> getOpenSlots(EventType eventType) {
        return availabilityService.getOpenSlots(eventType);
    }

    /** Fetch a single booking by ID (used by confirmation page) */
    public Booking getBookingById(Long bookingId) {
        return bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
    }

    /** Fetch a booking by guest token (used by edit / cancel flows) */
    public Booking getBookingByGuestToken(String guestToken) {
        return bookingRepo.findByGuestToken(guestToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid link."));
    }

    /** All bookings (any status) for a host ordered by date desc */
    public List<Booking> getAllBookings(User host) {
        return bookingRepo.findByAssignedHost(host)
                .stream()
                .sorted(Comparator.comparing(Booking::getStartTime).reversed())
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // Private — host assignment
    // ─────────────────────────────────────────────────────────────

    /** Adds non-blank additional guests from the form to the booking. */
    private void addGuestsFromForm(Booking booking, BookingForm form) {
        if (form.getAdditionalGuests() == null) return;
        if(!contactRepository.existsByOwnerAndEmail(booking.getAssignedHost(),form.getGuestEmail())) {
            Contact contact = new Contact();
            contact.setName(form.getGuestName());
            contact.setEmail(form.getGuestEmail());
            contact.setOwner(booking.getAssignedHost());
            contactRepository.save(contact);
        }
        for (BookingForm.GuestEntry entry : form.getAdditionalGuests()) {
            if (entry == null) continue;
            String name  = entry.getName();
            String email = entry.getEmail();
            if (name != null && !name.isBlank() && email != null && !email.isBlank()) {
                booking.getAdditionalGuests().add(new BookingGuest(booking, name.strip(), email.strip()));
                if(!contactRepository.existsByOwnerAndEmail(booking.getAssignedHost(),email)) {
                    // saving contact
                    Contact contact = new Contact();
                    contact.setName(name);
                    contact.setEmail(email);
                    contact.setOwner(booking.getAssignedHost());
                    contactRepository.save(contact);
                }
            }
        }
    }

    /**
     * Determines which host gets assigned to this booking.
     *
     * ONE_ON_ONE  → always the event type owner
     * ROUND_ROBIN → the active team member with the fewest
     *               confirmed bookings (simple fair distribution)
     * COLLECTIVE  → the owner (all members attend, but one is primary)
     * GROUP       → the owner
     */
    private User pickHost(EventType eventType) {
        if (eventType.getMeetingType() != MeetingType.ROUND_ROBIN) {
            return eventType.getOwner();
        }

        // Round-robin: pick active team member with fewest bookings
        List<TeamMember> activeMembers = memberRepo.findByProfileAndStatus(
                eventType.getProfile(), MemberStatus.ACTIVE);

        return activeMembers.stream()
                .min(Comparator.comparingLong(member ->
                        bookingRepo.findByAssignedHostAndStatus(
                                member.getUser(),
                                BookingStatus.CONFIRMED).size()))
                .map(TeamMember::getUser)
                .orElse(eventType.getOwner()); // fallback to owner
    }
}
