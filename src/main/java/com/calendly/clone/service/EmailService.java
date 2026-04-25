package com.calendly.clone.service;


import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.BookingGuest;
import com.calendly.clone.entity.TeamInvite;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * EmailService
 *
 * Central place for ALL outbound emails in the app.
 * Every method is @Async — Spring runs them in a background
 * thread so SMTP never blocks the HTTP response.
 *
 * Dependencies used:
 *   - JavaMailSender        (spring-boot-starter-mail)
 *   - SpringTemplateEngine  (spring-boot-starter-thymeleaf)
 *   - @Async                (spring-context, enabled via @EnableAsync)
 *
 * Templates live in:
 *   src/main/resources/templates/email/
 *     invite.html
 *     booking-guest.html
 *     booking-host.html
 *     booking-cancelled.html
 */
@Service
public class EmailService {

    private final JavaMailSender      mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender,
                        SpringTemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
    }

    // ─────────────────────────────────────────────────────────────
    // INVITE EMAILS
    // ─────────────────────────────────────────────────────────────

    /**
     * Sends a team invite email to the invitee.
     * Called by TeamInviteService after saving the TeamInvite row.
     *
     * Template variables available in invite.html:
     *   ${profileName}  — display name of the team
     *   ${joinLink}     — full URL with token
     *   ${expiryDays}   — how many days until invite expires
     */
    @Async
    public void sendTeamInviteEmail(TeamInvite invite) {
        Context ctx = new Context();
        ctx.setVariable("profileName", invite.getProfile().getDisplayName());
        ctx.setVariable("joinLink",
                baseUrl + "/join?token=" + invite.getToken());
        ctx.setVariable("expiryDays", 7);
        ctx.setVariable("role", invite.getIntendedRole().name());

        sendHtmlEmail(
                invite.getInvitedEmail(),
                "You're invited to join " + invite.getProfile().getDisplayName(),
                "invite",
                ctx
        );
    }

    // ─────────────────────────────────────────────────────────────
    // BOOKING EMAILS
    // ─────────────────────────────────────────────────────────────

    /**
     * Sends a confirmation email to the GUEST after booking.
     *
     * Template variables available in booking-guest.html:
     *   ${booking}      — full Booking object
     *   ${cancelLink}   — URL with guestToken for self-service cancel
     *   ${eventTitle}   — event type title
     *   ${hostName}     — assigned host display name
     */
    @Async
    public void sendBookingConfirmationToGuest(Booking booking) {
        String subject    = "Your booking is confirmed — " + booking.getEventType().getTitle();
        String cancelLink = baseUrl + "/cancel/" + booking.getGuestToken();

        // Primary guest
        sendGuestConfirmationEmail(booking.getGuestEmail(), booking.getGuestName(),
                subject, booking, cancelLink, "booking-guest");

        // Additional guests
        for (BookingGuest guest : booking.getAdditionalGuests()) {
            sendGuestConfirmationEmail(guest.getEmail(), guest.getName(),
                    "You're invited — " + booking.getEventType().getTitle(),
                    booking, cancelLink, "booking-guest");
        }
    }

    @Async
    public void sendBookingUpdateConfirmationToGuest(Booking booking) {
        String subject    = "Your booking has been updated — " + booking.getEventType().getTitle();
        String cancelLink = baseUrl + "/cancel/" + booking.getGuestToken();

        sendGuestConfirmationEmail(booking.getGuestEmail(), booking.getGuestName(),
                subject, booking, cancelLink, "booking-updated-guest");

        for (BookingGuest guest : booking.getAdditionalGuests()) {
            sendGuestConfirmationEmail(guest.getEmail(), guest.getName(),
                    "Meeting updated — " + booking.getEventType().getTitle(),
                    booking, cancelLink, "booking-updated-guest");
        }
    }

    private void sendGuestConfirmationEmail(String to, String recipientName,
                                            String subject, Booking booking,
                                            String cancelLink, String template) {
        Context ctx = new Context();
        ctx.setVariable("booking",       booking);
        ctx.setVariable("recipientName", recipientName);
        ctx.setVariable("eventTitle",    booking.getEventType().getTitle());
        ctx.setVariable("hostName",      booking.getAssignedHost().getProfile().getDisplayName());
        ctx.setVariable("cancelLink",    cancelLink);
        sendHtmlEmail(to, subject, template, ctx, buildIcsBytes(booking));
    }

    /**
     * Sends a new-booking notification email to the HOST.
     *
     * Template variables available in booking-host.html:
     *   ${booking}      — full Booking object
     *   ${guestName}    — guest's name
     *   ${guestEmail}   — guest's email
     *   ${startTime}    — formatted start time
     */
    @Async
    public void sendBookingNotificationToHost(Booking booking) {
        Context ctx = new Context();
        ctx.setVariable("booking",    booking);
        ctx.setVariable("guestName",  booking.getGuestName());
        ctx.setVariable("guestEmail", booking.getGuestEmail());
        ctx.setVariable("startTime", formatEmailTime(booking));
        ctx.setVariable("baseUrl",    baseUrl);

        sendHtmlEmail(
                booking.getAssignedHost().getEmail(),
                "New booking: " + booking.getGuestName()
                        + " — " + booking.getEventType().getTitle(),
                "booking-host",
                ctx,
                buildIcsBytes(booking)
        );
    }

    @Async
    public void sendBookingUpdateNotificationToHost(Booking booking) {
        Context ctx = new Context();
        ctx.setVariable("booking",    booking);
        ctx.setVariable("guestName",  booking.getGuestName());
        ctx.setVariable("guestEmail", booking.getGuestEmail());
        ctx.setVariable("startTime", formatEmailTime(booking));
        ctx.setVariable("baseUrl",    baseUrl);

        sendHtmlEmail(
                booking.getAssignedHost().getEmail(),
                "Booking updated: " + booking.getGuestName()
                        + " — " + booking.getEventType().getTitle(),
                "booking-updated-host",
                ctx,
                buildIcsBytes(booking)
        );
    }

    /**
     * Notifies the HOST that a guest cancelled their booking.
     */
    @Async
    public void sendGuestCancellationNotificationToHost(Booking booking) {
        Context ctx = new Context();
        ctx.setVariable("booking",    booking);
        ctx.setVariable("guestName",  booking.getGuestName());
        ctx.setVariable("guestEmail", booking.getGuestEmail());
        ctx.setVariable("eventTitle", booking.getEventType().getTitle());
        ctx.setVariable("baseUrl",    baseUrl);

        sendHtmlEmail(
                booking.getAssignedHost().getEmail(),
                booking.getGuestName() + " cancelled their booking — "
                        + booking.getEventType().getTitle(),
                "booking-cancelled-host",
                ctx
        );
    }

    /**
     * Sends a cancellation email to the guest when a booking is cancelled.
     */
    @Async
    public void sendCancellationEmail(Booking booking) {
        Context ctx = new Context();
        ctx.setVariable("booking",    booking);
        ctx.setVariable("eventTitle", booking.getEventType().getTitle());

        sendHtmlEmail(
                booking.getGuestEmail(),
                "Your booking has been cancelled — "
                        + booking.getEventType().getTitle(),
                "booking-cancelled",
                ctx
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Private helper — shared by all methods above
    // ─────────────────────────────────────────────────────────────

    /**
     * Renders a Thymeleaf template into HTML and sends it via SMTP.
     *
     * @param to           recipient email address
     * @param subject      email subject line
     * @param templateName path under templates/ (without .html)
     * @param ctx          Thymeleaf context with template variables
     */
    /** Sends HTML email without a calendar attachment (invites, cancellations). */
    private void sendHtmlEmail(String to, String subject,
                               String templateName, Context ctx) {
        sendHtmlEmail(to, subject, templateName, ctx, null);
    }

    /** Sends HTML email with an optional ICS calendar attachment. */
    private void sendHtmlEmail(String to, String subject,
                               String templateName, Context ctx, byte[] icsBytes) {
        try {
            String htmlBody = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (icsBytes != null) {
                helper.addAttachment("invite.ics",
                        new ByteArrayResource(icsBytes),
                        "text/calendar; charset=utf-8; method=REQUEST");
            }

            mailSender.send(message);

        } catch (MessagingException e) {
            System.err.println("[EmailService] Failed to send to "
                    + to + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ICS (iCalendar) builder
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a RFC-5545 iCalendar byte array for the given booking.
     * Attaching this to an email causes Gmail / Outlook / Apple Mail
     * to show an inline "Add to Calendar" prompt.
     */
//    private byte[] buildIcsBytes(Booking booking) {
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
//
//        String dtStart    = booking.getStartTime().format(fmt);
//        String dtEnd      = booking.getEndTime().format(fmt);
//        String dtStamp    = java.time.LocalDateTime.now().format(fmt);
//        String uid        = "booking-" + booking.getId() + "@calendlyclone.app";
//        String summary    = icsEscape(booking.getEventType().getTitle());
//        String hostName   = icsEscape(booking.getAssignedHost().getProfile().getDisplayName());
//        String hostEmail  = booking.getAssignedHost().getEmail();
//        String guestName  = icsEscape(booking.getGuestName());
//        String guestEmail = booking.getGuestEmail();
//        String meetingLink = booking.getEventType().getMeetingLink();
//
//        StringBuilder sb = new StringBuilder();
//        sb.append("BEGIN:VCALENDAR\r\n");
//        sb.append("VERSION:2.0\r\n");
//        sb.append("PRODID:-//Calendly Clone//EN\r\n");
//        sb.append("CALSCALE:GREGORIAN\r\n");
//        sb.append("METHOD:REQUEST\r\n");
//        sb.append("BEGIN:VEVENT\r\n");
//        sb.append("UID:").append(uid).append("\r\n");
//        sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
//        sb.append("DTSTART:").append(dtStart).append("\r\n");
//        sb.append("DTEND:").append(dtEnd).append("\r\n");
//        sb.append("SUMMARY:").append(summary).append("\r\n");
//        sb.append("ORGANIZER;CN=\"").append(hostName)
//          .append("\":mailto:").append(hostEmail).append("\r\n");
//        sb.append("ATTENDEE;CN=\"").append(guestName)
//          .append("\";ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:")
//          .append(guestEmail).append("\r\n");
//
//        if (meetingLink != null && !meetingLink.isBlank()) {
//            sb.append("LOCATION:").append(icsEscape(meetingLink)).append("\r\n");
//            sb.append("DESCRIPTION:Join meeting: ")
//              .append(icsEscape(meetingLink)).append("\r\n");
//        } else if (booking.getGuestNotes() != null && !booking.getGuestNotes().isBlank()) {
//            sb.append("DESCRIPTION:").append(icsEscape(booking.getGuestNotes())).append("\r\n");
//        }
//
//        sb.append("STATUS:CONFIRMED\r\n");
//        sb.append("END:VEVENT\r\n");
//        sb.append("END:VCALENDAR\r\n");
//
//        return sb.toString().getBytes(StandardCharsets.UTF_8);
//    }
    private byte[] buildIcsBytes(Booking booking) {

        String zoneId =
                booking.getAssignedHost().getProfile().getTimezone();
        // Better later:
        // String zoneId = booking.getAssignedHost()
        //        .getProfile().getTimezone();

        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

        String dtStart = booking.getStartTime().format(fmt);
        String dtEnd   = booking.getEndTime().format(fmt);
        String dtStamp = java.time.LocalDateTime.now().format(fmt);

        String uid        = "booking-" + booking.getId() + "@calendlyclone.app";
        String summary    = icsEscape(booking.getEventType().getTitle());
        String hostName   = icsEscape(
                booking.getAssignedHost().getProfile().getDisplayName());
        String hostEmail  = booking.getAssignedHost().getEmail();
        String guestName  = icsEscape(booking.getGuestName());
        String guestEmail = booking.getGuestEmail();
        String meetingLink = booking.getEventType().getMeetingLink();

        StringBuilder sb = new StringBuilder();

        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Calendly Clone//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:REQUEST\r\n");

        /* Timezone block */
        sb.append("BEGIN:VTIMEZONE\r\n");
        sb.append("TZID:").append(zoneId).append("\r\n");
        sb.append("END:VTIMEZONE\r\n");

        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(uid).append("\r\n");
        sb.append("DTSTAMP:").append(dtStamp).append("\r\n");

        sb.append("DTSTART;TZID=").append(zoneId)
                .append(":").append(dtStart).append("\r\n");

        sb.append("DTEND;TZID=").append(zoneId)
                .append(":").append(dtEnd).append("\r\n");

        sb.append("SUMMARY:").append(summary).append("\r\n");

        sb.append("ORGANIZER;CN=\"").append(hostName)
                .append("\":mailto:").append(hostEmail).append("\r\n");

        sb.append("ATTENDEE;CN=\"").append(guestName)
                .append("\";ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:")
                .append(guestEmail).append("\r\n");

        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append("LOCATION:")
                    .append(icsEscape(meetingLink)).append("\r\n");

            sb.append("DESCRIPTION:Join meeting: ")
                    .append(icsEscape(meetingLink)).append("\r\n");
        } else if (booking.getGuestNotes() != null &&
                !booking.getGuestNotes().isBlank()) {

            sb.append("DESCRIPTION:")
                    .append(icsEscape(booking.getGuestNotes()))
                    .append("\r\n");
        }

        sb.append("STATUS:CONFIRMED\r\n");
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Escapes special characters per RFC 5545 §3.3.11. */
    private String icsEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace(";",  "\\;")
                    .replace(",",  "\\,")
                    .replace("\n", "\\n")
                    .replace("\r", "");
    }

    private String formatEmailTime(Booking booking) {
        return booking.getStartTime()
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"));
    }
}