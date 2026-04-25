package com.calendly.clone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileForm {

    @NotBlank
    @Size(max = 100)
    private String displayName;

    @Size(max = 500)
    private String bio;

    @NotBlank
    @Size(min = 2, max = 80)
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*[a-z0-9]$",
             message = "Slug must be lowercase letters, numbers, and hyphens only, and cannot start or end with a hyphen")
    private String bookingPageSlug;

    public String getDisplayName()                    { return displayName; }
    public void   setDisplayName(String displayName)  { this.displayName = displayName; }
    public String getBio()                            { return bio; }
    public void   setBio(String bio)                  { this.bio = bio; }
    public String getBookingPageSlug()                { return bookingPageSlug; }
    public void   setBookingPageSlug(String slug)     { this.bookingPageSlug = slug; }
}
