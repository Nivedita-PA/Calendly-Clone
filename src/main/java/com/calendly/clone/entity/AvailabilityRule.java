package com.calendly.clone.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "availability_rules",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "day_of_week"}))
public class AvailabilityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    public AvailabilityRule() {}

    public AvailabilityRule(User user, DayOfWeek dayOfWeek, boolean enabled,
                            LocalTime startTime, LocalTime endTime) {
        this.user      = user;
        this.dayOfWeek = dayOfWeek;
        this.enabled   = enabled;
        this.startTime = startTime;
        this.endTime   = endTime;
    }

    public Long getId()                         { return id; }
    public User getUser()                       { return user; }
    public void setUser(User user)              { this.user = user; }
    public DayOfWeek getDayOfWeek()             { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek d)       { this.dayOfWeek = d; }
    public boolean isEnabled()                  { return enabled; }
    public void setEnabled(boolean enabled)     { this.enabled = enabled; }
    public LocalTime getStartTime()             { return startTime; }
    public void setStartTime(LocalTime t)       { this.startTime = t; }
    public LocalTime getEndTime()               { return endTime; }
    public void setEndTime(LocalTime t)         { this.endTime = t; }
}
