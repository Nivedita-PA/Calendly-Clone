package com.calendly.clone.controller;

import com.calendly.clone.dto.EventTypeForm;
import com.calendly.clone.entity.EventType;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.User;
import com.calendly.clone.entity.enums.MeetingType;
import com.calendly.clone.repository.EventTypeRepository;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.repository.UserRepository;
import com.calendly.clone.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/event-types")
public class EventTypeController {

    private final EventTypeRepository eventTypeRepo;
    private final UserRepository      userRepo;
    private final ProfileRepository   profileRepo;
    private final ContactService      contactService;

    public EventTypeController(EventTypeRepository eventTypeRepo,
                               UserRepository userRepo,
                               ProfileRepository profileRepo, ContactService contactService) {
        this.eventTypeRepo = eventTypeRepo;
        this.userRepo      = userRepo;
        this.profileRepo   = profileRepo;
        this.contactService = contactService;
    }

    // ── CREATE ───────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new EventTypeForm());
        model.addAttribute("meetingTypes", MeetingType.values());
        model.addAttribute("editing", false);
        return "event-type-form";
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @Valid @ModelAttribute("form") EventTypeForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {

        if (result.hasErrors()) {
            model.addAttribute("meetingTypes", MeetingType.values());
            model.addAttribute("editing", false);
            return "event-type-form";
        }

        User    user    = resolveUser(principal);
        Profile profile = resolveProfile(user);
        String  slug    = slugify(form.getTitle(), profile);

        EventType et = new EventType(user, profile, form.getTitle(), slug, form.getDurationMinutes());
        et.setDescription(form.getDescription());
        et.setBufferBefore(form.getBufferBefore());
        et.setBufferAfter(form.getBufferAfter());
        et.setMeetingType(form.getMeetingType());
        et.setColor(form.getColor());
        et.setMeetingLink(form.getMeetingLink());
        eventTypeRepo.save(et);

        flash.addFlashAttribute("success", "Event type created.");
        return "redirect:/dashboard";
    }

    // ── EDIT ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails principal,
                           Model model) {

        EventType et = loadOwned(id, principal);

        EventTypeForm form = new EventTypeForm();
        form.setTitle(et.getTitle());
        form.setDescription(et.getDescription());
        form.setDurationMinutes(et.getDurationMinutes());
        form.setBufferBefore(et.getBufferBefore());
        form.setBufferAfter(et.getBufferAfter());
        form.setMeetingType(et.getMeetingType());
        form.setColor(et.getColor());
        form.setMeetingLink(et.getMeetingLink());

        model.addAttribute("form", form);
        model.addAttribute("eventTypeId", id);
        model.addAttribute("meetingTypes", MeetingType.values());
        model.addAttribute("editing", true);
        return "event-type-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         @Valid @ModelAttribute("form") EventTypeForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {

        if (result.hasErrors()) {
            model.addAttribute("eventTypeId", id);
            model.addAttribute("meetingTypes", MeetingType.values());
            model.addAttribute("editing", true);
            return "event-type-form";
        }

        EventType et = loadOwned(id, principal);
        et.setTitle(form.getTitle());
        et.setDescription(form.getDescription());
        et.setDurationMinutes(form.getDurationMinutes());
        et.setBufferBefore(form.getBufferBefore());
        et.setBufferAfter(form.getBufferAfter());
        et.setMeetingType(form.getMeetingType());
        et.setColor(form.getColor());
        et.setMeetingLink(form.getMeetingLink());
        eventTypeRepo.save(et);

        flash.addFlashAttribute("success", "Event type updated.");
        return "redirect:/dashboard";
    }

    // ── SOFT DELETE ──────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes flash) {
        EventType et = loadOwned(id, principal);
        et.setActive(false);
        eventTypeRepo.save(et);
        flash.addFlashAttribute("success", "Event type removed.");
        return "redirect:/dashboard";
    }

    @GetMapping("/showEvents/{contactId}")
    public String showAllEventsByContact(@PathVariable Long contactId,Model model, @AuthenticationPrincipal UserDetails userDetails){
        User user = resolveUser(userDetails);
        model.addAttribute("user", user);
        model.addAttribute("contact", contactService.searchById(contactId));
        model.addAttribute("eventTypes", eventTypeRepo.findEventTypesForContact(user, contactService.searchById(contactId).getEmail()));
        return "event-types";
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private User resolveUser(UserDetails principal) {
        return userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Profile resolveProfile(User user) {
        return profileRepo.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * Loads an EventType and verifies the current user owns it.
     * Throws 403 if they don't.
     */
    private EventType loadOwned(Long id, UserDetails principal) {
        EventType et = eventTypeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!et.getOwner().getEmail().equals(principal.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return et;
    }

    /**
     * Converts a title to a URL-safe slug, appending a counter
     * if the slug is already taken within this profile.
     */
    private String slugify(String title, Profile profile) {
        String base = title.toLowerCase()
                           .replaceAll("[^a-z0-9]+", "-")
                           .replaceAll("^-|-$", "");
        String slug = base;
        int counter = 1;
        while (eventTypeRepo.findByProfileAndSlug(profile, slug).isPresent()) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
