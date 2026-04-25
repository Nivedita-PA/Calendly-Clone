package com.calendly.clone.dto;

import com.calendly.clone.entity.enums.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Form data submitted by a team admin when inviting a new member.
 * Validated by @Valid in TeamInviteController.
 */
public class InviteForm {

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email")
    private String email;

    @NotNull(message = "Please select a role")
    private MemberRole role = MemberRole.MEMBER;

    // ── Getters & Setters ──────────────────────────────────
    public String getEmail()             { return email; }
    public void setEmail(String email)   { this.email = email; }
    public MemberRole getRole()          { return role; }
    public void setRole(MemberRole role) { this.role = role; }
}