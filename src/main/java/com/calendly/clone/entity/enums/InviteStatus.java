package com.calendly.clone.entity.enums;

public enum InviteStatus {
    PENDING,   // email sent, waiting for invitee to accept
    ACCEPTED,  // invitee accepted → TeamMember row created
    EXPIRED,   // passed expiresAt without action
    REVOKED    // manually cancelled by an admin
}