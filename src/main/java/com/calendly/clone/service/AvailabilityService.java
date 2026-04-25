package com.calendly.clone.service;

import com.calendly.clone.entity.AvailabilityRule;
import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.EventType;
import com.calendly.clone.entity.User;
import com.calendly.clone.repository.AvailabilityRuleRepository;
import com.calendly.clone.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Service
public class AvailabilityService {

    private final BookingRepository          bookingRepo;
    private final AvailabilityRuleRepository availabilityRepo;

    /** How many days ahead to show available slots */
    private static final int DAYS_AHEAD = 30;

    public AvailabilityService(BookingRepository bookingRepo,
                               AvailabilityRuleRepository availabilityRepo) {
        this.bookingRepo      = bookingRepo;
        this.availabilityRepo = availabilityRepo;
    }

    // ─────────────────────────────────────────────────────────────
    // Main public method
    // ─────────────────────────────────────────────────────────────

    public List<LocalDateTime> getOpenSlots(EventType eventType) {
        User host         = eventType.getOwner();
        int  duration     = eventType.getDurationMinutes();
        int  bufferBefore = eventType.getBufferBefore();
        int  bufferAfter  = eventType.getBufferAfter();
        int  slotBlock    = duration + bufferBefore + bufferAfter;

        Map<DayOfWeek, LocalTime[]> hours = loadHours(host);

        // Use host's timezone so "today" and "now" are correct for their location
        ZoneId hostZone = resolveZone(eventType.getProfile().getTimezone());

        LocalDateTime now       = LocalDateTime.now(hostZone);
        LocalDate     today     = now.toLocalDate();
        LocalDateTime windowEnd = today.plusDays(DAYS_AHEAD).atTime(23, 59);

        List<Booking> existing = bookingRepo.findConfirmedInRange(host, now, windowEnd);
        List<LocalDateTime> openSlots = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < DAYS_AHEAD; dayOffset++) {
            LocalDate day     = today.plusDays(dayOffset);
            DayOfWeek weekDay = day.getDayOfWeek();

            if (!hours.containsKey(weekDay)) continue;

            LocalTime[] window   = hours.get(weekDay);
            LocalTime   dayStart = window[0];
            LocalTime   dayEnd   = window[1];

            LocalTime cursor = dayStart;
            while (!cursor.plusMinutes(duration).isAfter(dayEnd)) {
                LocalDateTime slotStart = day.atTime(cursor);
                LocalDateTime slotEnd   = slotStart.plusMinutes(duration);

                if (slotStart.isBefore(now.plusHours(1))) {
                    cursor = cursor.plusMinutes(slotBlock);
                    continue;
                }

                if (!hasConflict(slotStart, slotEnd, existing, bufferBefore, bufferAfter)) {
                    openSlots.add(slotStart);
                }

                cursor = cursor.plusMinutes(slotBlock);
            }
        }

        return openSlots;
    }

    // ─────────────────────────────────────────────────────────────
    // Load availability rules for a host
    // ─────────────────────────────────────────────────────────────

    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END   = LocalTime.of(17, 0);

    private Map<DayOfWeek, LocalTime[]> loadHours(User host) {
        List<AvailabilityRule> rules = availabilityRepo.findByUserOrderByDayOfWeek(host);
        Map<DayOfWeek, LocalTime[]> map = new EnumMap<>(DayOfWeek.class);
        for (AvailabilityRule r : rules) {
            if (r.isEnabled()) {
                map.put(r.getDayOfWeek(), new LocalTime[]{r.getStartTime(), r.getEndTime()});
            }
        }
        // Fallback for users who have no availability rules yet
        if (map.isEmpty()) {
            LocalTime[] defaultWindow = {DEFAULT_START, DEFAULT_END};
            for (DayOfWeek day : new DayOfWeek[]{
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
                map.put(day, defaultWindow);
            }
        }
        return map;
    }

    // ─────────────────────────────────────────────────────────────
    // Conflict check
    // ─────────────────────────────────────────────────────────────

    private boolean hasConflict(LocalDateTime slotStart,
                                LocalDateTime slotEnd,
                                List<Booking> existing,
                                int bufferBefore,
                                int bufferAfter) {
        LocalDateTime effectiveStart = slotStart.minusMinutes(bufferBefore);
        LocalDateTime effectiveEnd   = slotEnd.plusMinutes(bufferAfter);

        for (Booking b : existing) {
            if (b.getStartTime().isBefore(effectiveEnd)
                    && b.getEndTime().isAfter(effectiveStart)) {
                return true;
            }
        }
        return false;
    }

    private ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneId.of("UTC");
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
