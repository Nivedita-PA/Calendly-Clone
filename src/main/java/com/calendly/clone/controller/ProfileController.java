package com.calendly.clone.controller;

import com.calendly.clone.dto.ProfileForm;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.User;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository    userRepo;
    private final ProfileRepository profileRepo;

    public ProfileController(UserRepository userRepo, ProfileRepository profileRepo) {
        this.userRepo    = userRepo;
        this.profileRepo = profileRepo;
    }

    @GetMapping("/edit")
    public String showEditForm(@AuthenticationPrincipal UserDetails principal, Model model) {
        Profile profile = resolveProfile(principal);

        ProfileForm form = new ProfileForm();
        form.setDisplayName(profile.getDisplayName());
        form.setBio(profile.getBio());
        form.setBookingPageSlug(profile.getBookingPageSlug());

        model.addAttribute("profileForm", form);
        model.addAttribute("profile",     profile);
        return "profile-edit";
    }

    @PostMapping("/edit")
    public String saveProfile(@AuthenticationPrincipal UserDetails principal,
                              @Valid @ModelAttribute("profileForm") ProfileForm form,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttrs) {

        Profile profile = resolveProfile(principal);

        if (result.hasErrors()) {
            model.addAttribute("profile", profile);
            return "profile-edit";
        }

        // Slug uniqueness check — only enforce if it changed
        String newSlug = form.getBookingPageSlug().toLowerCase();
        if (!newSlug.equals(profile.getBookingPageSlug())
                && profileRepo.existsByBookingPageSlug(newSlug)) {
            result.rejectValue("bookingPageSlug", "duplicate",
                    "That URL is already taken. Please choose another.");
            model.addAttribute("profile", profile);
            return "profile-edit";
        }

        profile.setDisplayName(form.getDisplayName());
        profile.setBio(form.getBio() != null ? form.getBio().strip() : null);
        profile.setBookingPageSlug(newSlug);
        profileRepo.save(profile);

        redirectAttrs.addFlashAttribute("success", "Profile updated.");
        return "redirect:/profile/edit";
    }

    private Profile resolveProfile(UserDetails principal) {
        User user = userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return profileRepo.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
