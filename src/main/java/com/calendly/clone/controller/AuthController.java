package com.calendly.clone.controller;

import com.calendly.clone.dto.RegistrationForm;
import com.calendly.clone.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ── Login ────────────────────────────────────────────────────────

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String loggedOut,
                            Model model) {
        if (error != null)     model.addAttribute("loginError", "Invalid email or password.");
        if (loggedOut != null) model.addAttribute("loggedOut", true);
        return "login";
    }

    // ── Register ─────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String token,
                               Model model) {
        model.addAttribute("form", new RegistrationForm());
        model.addAttribute("inviteToken", token);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult result,
                           @RequestParam(required = false) String token,
                           Model model,
                           RedirectAttributes flash) {

        // Check password confirmation
        if (!form.passwordsMatch()) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }

        if (result.hasErrors()) {
            model.addAttribute("inviteToken", token);
            return "register";
        }

        try {
            userService.register(form);
            flash.addFlashAttribute("success", "Account created! Please sign in.");
            if (token != null && !token.isBlank()) {
                return "redirect:/login?token=" + token;
            }
            return "redirect:/login";

        } catch (IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("inviteToken", token);
            return "register";
        }
    }
}
