package com.calendly.clone.service;

import com.calendly.clone.dto.RegistrationForm;
import com.calendly.clone.entity.AvailabilityRule;
import com.calendly.clone.entity.Profile;
import com.calendly.clone.entity.User;
import com.calendly.clone.repository.AvailabilityRuleRepository;
import com.calendly.clone.repository.ProfileRepository;
import com.calendly.clone.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository             userRepo;
    private final ProfileRepository          profileRepo;
    private final AvailabilityRuleRepository availabilityRepo;
    private final PasswordEncoder            passwordEncoder;

    public UserService(UserRepository userRepo,
                       ProfileRepository profileRepo,
                       AvailabilityRuleRepository availabilityRepo,
                       PasswordEncoder passwordEncoder) {
        this.userRepo         = userRepo;
        this.profileRepo      = profileRepo;
        this.availabilityRepo = availabilityRepo;
        this.passwordEncoder  = passwordEncoder;
    }

    // ── Spring Security hook ─────────────────────────────────────────

    /**
     * Spring Security calls this to load a user by their login identifier.
     * We use email as the login identifier (matches our login form).
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                .accountLocked(!user.isEnabled())
                .build();
    }

    // ── Registration ─────────────────────────────────────────────────

    /**
     * Creates a new User + their default Profile in one transaction.
     * Validates uniqueness of email and username before saving.
     *
     * @param form validated registration form
     * @return the newly created User
     * @throws IllegalStateException if email or username is already taken
     */
    @Transactional
    public User register(RegistrationForm form) {

        if (userRepo.existsByEmail(form.getEmail())) {
            throw new IllegalStateException("That email is already registered.");
        }
        if (userRepo.existsByUsername(form.getUsername())) {
            throw new IllegalStateException("That username is already taken.");
        }

        // Save user with hashed password
        User user = new User(
                form.getUsername(),
                form.getEmail(),
                passwordEncoder.encode(form.getPassword())
        );
        userRepo.save(user);

        // Automatically create their personal booking profile
        String slug = buildUniqueSlug(form.getUsername());
        Profile profile = new Profile(user, form.getUsername(), slug);
        profile.setTimezone(java.time.ZoneId.systemDefault().getId());
        profileRepo.save(profile);

        // Seed default Mon–Fri 9:00–17:00 availability rules
        LocalTime nine = LocalTime.of(9, 0);
        LocalTime five = LocalTime.of(17, 0);
        for (DayOfWeek day : new DayOfWeek[]{
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            availabilityRepo.save(new AvailabilityRule(user, day, true, nine, five));
        }
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.SATURDAY, DayOfWeek.SUNDAY}) {
            availabilityRepo.save(new AvailabilityRule(user, day, false, nine, five));
        }

        return user;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Converts a username to a URL-safe slug, appending a numeric
     * suffix if the slug is already taken.
     */
    private String buildUniqueSlug(String username) {
        String base = username.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                              .replaceAll("^-|-$", "");
        String slug = base;
        int counter = 1;
        while (profileRepo.existsByBookingPageSlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
