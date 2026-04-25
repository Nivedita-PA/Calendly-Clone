package com.calendly.clone.entity.enums;

public enum MeetingType {
    ONE_ON_ONE,  // one host, one guest
    GROUP,       // one host, many guests share the same slot
    ROUND_ROBIN, // one guest, assigned to next available host in team
    COLLECTIVE   // one guest, ALL team members must be free simultaneously
}