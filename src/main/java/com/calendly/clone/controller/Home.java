package com.calendly.clone.controller;

import org.springframework.stereotype.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

/**
 * HomeController
 *
 * Handles all requests to the public-facing home/landing page.
 * This page is fully public — no authentication required.
 *
 * URL: GET /
 * Template: src/main/resources/templates/home.html
 */
@Controller
public class Home {

    // ──────────────────────────────────────────────
    // Injected config values (set these in
    // src/main/resources/application.properties)
    // ──────────────────────────────────────────────

    /** Total bookings made today — could be pulled live from DB */
    @Value("${home.bookings-today:4200}")
    private int bookingsToday;

    /** Toggle the stats band on/off without touching HTML */
    @Value("${home.show-stats:true}")
    private boolean showStats;

    /** Short description shown below the hero headline */
    @Value("${home.hero-description:Share your link. Let others pick a time that works. " +
            "No back-and-forth emails, no double-bookings, no friction \u2014 just meetings that happen.}")
    private String heroDescription;

    // ──────────────────────────────────────────────
    // Main handler
    // ──────────────────────────────────────────────

    /**
     * GET /
     *
     * Renders the landing page. Adds all the data Thymeleaf
     * needs to the Model. No authentication guard — this page
     * is accessible to everyone.
     *
     * @param model Spring MVC Model passed to the template
     * @return the logical view name "home" → resolves to home.html
     */
    @GetMapping("/")
    public String home(Model model) {

        // ── Hero section ──────────────────────────
        model.addAttribute("totalBookingsToday", formatNumber(bookingsToday));
        model.addAttribute("heroDescription", heroDescription);

        // ── Demo booking card (right side of hero) ──
        model.addAttribute("demoHostName", "Nivedita Pal");
        model.addAttribute("demoHostInitials", "NP");

        // ── Trusted company logos strip ───────────
        model.addAttribute("trustedCompanies", trustedCompanies());

        // ── Stats band ────────────────────────────
        model.addAttribute("showStats", showStats);
        model.addAttribute("stats", buildStats());

        // ── Footer ────────────────────────────────
        model.addAttribute("currentYear", LocalDate.now().getYear());

        return "home"; // → templates/home.html
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * Returns the list of company names shown in the
     * "Trusted by teams at…" strip.
     *
     * In a real app you might load these from the DB or
     * a config file; here they are hard-coded for simplicity.
     */
    private List<String> trustedCompanies() {
        return List.of(

        );
    }

    /**
     * Builds the three hero stat cards shown below the features grid.
     * In a production app these numbers would come from a service/repo.
     */
    private List<Stat> buildStats() {
        return List.of(
                new Stat("10K+",   "meetings scheduled"),
                new Stat("98%",    "guest satisfaction"),
                new Stat("< 30s",  "average booking time")
        );
    }

    /**
     * Formats a plain integer into a human-readable string.
     * e.g. 4200 → "4,200"
     */
    private String formatNumber(int n) {
        return String.format("%,d", n);
    }

    // ──────────────────────────────────────────────
    // Inner record — Stat card data
    // ──────────────────────────────────────────────

    /**
     * Simple value object for a single stat card.
     * Move to its own file (com.schedulr.model.Stat)
     * once the project grows.
     */
    public record Stat(String value, String label) {}
}