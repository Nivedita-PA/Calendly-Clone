package com.calendly.clone.controller;

import com.calendly.clone.dto.AvailabilityForm;
import com.calendly.clone.entity.AvailabilityRule;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.User;
import com.calendly.clone.repository.AvailabilityRuleRepository;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/availability")
public class AvailabilityController {

    private final UserRepository             userRepo;
    private final ProfileRepository          profileRepo;
    private final AvailabilityRuleRepository availabilityRepo;

    private static final DayOfWeek[] WEEK_ORDER = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    };

    /** Curated list of common IANA timezone IDs shown in the UI. */
    private static final List<String> TIMEZONES = List.of(
            "UTC",
            "America/New_York", "America/Chicago", "America/Denver",
            "America/Los_Angeles", "America/Anchorage", "America/Honolulu",
            "America/Toronto", "America/Vancouver", "America/Sao_Paulo",
            "America/Argentina/Buenos_Aires", "America/Bogota", "America/Mexico_City",
            "Europe/London", "Europe/Dublin", "Europe/Lisbon",
            "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Europe/Rome",
            "Europe/Amsterdam", "Europe/Stockholm", "Europe/Warsaw",
            "Europe/Helsinki", "Europe/Athens", "Europe/Istanbul",
            "Europe/Moscow", "Europe/Kiev",
            "Africa/Cairo", "Africa/Nairobi", "Africa/Lagos", "Africa/Johannesburg",
            "Asia/Dubai", "Asia/Riyadh", "Asia/Karachi",
            "Asia/Kolkata", "Asia/Dhaka", "Asia/Kathmandu",
            "Asia/Colombo", "Asia/Bangkok", "Asia/Jakarta",
            "Asia/Singapore", "Asia/Kuala_Lumpur", "Asia/Manila",
            "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Taipei",
            "Asia/Tokyo", "Asia/Seoul", "Asia/Ulaanbaatar",
            "Australia/Perth", "Australia/Darwin", "Australia/Adelaide",
            "Australia/Brisbane", "Australia/Sydney", "Australia/Melbourne",
            "Pacific/Auckland", "Pacific/Fiji", "Pacific/Honolulu"
    );

    public AvailabilityController(UserRepository userRepo,
                                  ProfileRepository profileRepo,
                                  AvailabilityRuleRepository availabilityRepo) {
        this.userRepo         = userRepo;
        this.profileRepo      = profileRepo;
        this.availabilityRepo = availabilityRepo;
    }

    @GetMapping
    public String showForm(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = resolveUser(principal);
        List<AvailabilityRule> rules = availabilityRepo.findByUserOrderByDayOfWeek(user);

        // Build a map for quick lookup
        Map<DayOfWeek, AvailabilityRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(AvailabilityRule::getDayOfWeek, r -> r));

        // Build form in canonical week order
        AvailabilityForm form = new AvailabilityForm();
        List<AvailabilityForm.DayEntry> entries = new ArrayList<>();
        for (DayOfWeek day : WEEK_ORDER) {
            AvailabilityForm.DayEntry entry = new AvailabilityForm.DayEntry();
            entry.setDayOfWeek(day.name());
            if (ruleMap.containsKey(day)) {
                AvailabilityRule r = ruleMap.get(day);
                entry.setEnabled(r.isEnabled());
                entry.setStartTime(r.getStartTime().toString());
                entry.setEndTime(r.getEndTime().toString());
            } else {
                entry.setEnabled(false);
                entry.setStartTime("09:00");
                entry.setEndTime("17:00");
            }
            entries.add(entry);
        }
        form.setDays(entries);

        Profile profile = profileRepo.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));

        model.addAttribute("availabilityForm",   form);
        model.addAttribute("user",               user);
        model.addAttribute("currentTimezone",    profile.getTimezone());
        model.addAttribute("availableTimezones", TIMEZONES);
        return "availability";
    }

    @PostMapping
    public String saveForm(@AuthenticationPrincipal UserDetails principal,
                           @ModelAttribute("availabilityForm") AvailabilityForm form,
                           RedirectAttributes redirectAttrs) {
        User user = resolveUser(principal);

        // Validate time ranges before deleting anything
        if (form.getDays() != null) {
            for (AvailabilityForm.DayEntry entry : form.getDays()) {
                if (!entry.isEnabled()) continue;
                try {
                    LocalTime start = LocalTime.parse(entry.getStartTime());
                    LocalTime end   = LocalTime.parse(entry.getEndTime());
                    if (!end.isAfter(start)) {
                        redirectAttrs.addFlashAttribute("error",
                                "End time must be after start time for " + friendlyDay(entry.getDayOfWeek()));
                        return "redirect:/availability";
                    }
                } catch (DateTimeParseException e) {
                    redirectAttrs.addFlashAttribute("error", "Invalid time format.");
                    return "redirect:/availability";
                }
            }
        }

        // Replace all rules for this user atomically
        availabilityRepo.deleteByUser(user);

        if (form.getDays() != null) {
            for (AvailabilityForm.DayEntry entry : form.getDays()) {
                DayOfWeek day = DayOfWeek.valueOf(entry.getDayOfWeek());
                LocalTime start = entry.getStartTime() != null
                        ? LocalTime.parse(entry.getStartTime()) : LocalTime.of(9, 0);
                LocalTime end = entry.getEndTime() != null
                        ? LocalTime.parse(entry.getEndTime()) : LocalTime.of(17, 0);
                availabilityRepo.save(new AvailabilityRule(user, day, entry.isEnabled(), start, end));
            }
        }

        redirectAttrs.addFlashAttribute("success", "Availability saved.");
        return "redirect:/availability";
    }

    @PostMapping("/timezone")
    public String saveTimezone(@AuthenticationPrincipal UserDetails principal,
                               @RequestParam String timezone,
                               RedirectAttributes redirectAttrs) {
        User user = resolveUser(principal);
        try {
            ZoneId.of(timezone); // validate IANA ID
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Invalid timezone: " + timezone);
            return "redirect:/availability";
        }

        Profile profile = profileRepo.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));
        profile.setTimezone(timezone);
        profileRepo.save(profile);

        redirectAttrs.addFlashAttribute("success", "Timezone updated to " + timezone + ".");
        return "redirect:/availability";
    }

    private User resolveUser(UserDetails principal) {
        return userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String friendlyDay(String day) {
        if (day == null) return "unknown day";
        String lower = day.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
