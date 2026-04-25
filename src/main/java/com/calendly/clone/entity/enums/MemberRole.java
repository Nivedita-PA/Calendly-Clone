package com.calendly.clone.entity.enums;

public enum MemberRole {
    OWNER,   // created the team — full control, can delete
    ADMIN,   // can invite/remove members, manage all event types
    MEMBER   // can only manage their own event types
}
