package com.calendly.clone.dto;

import java.util.List;

/**
 * Carries one row per day of the week from the availability settings form.
 */
public class AvailabilityForm {

    private List<DayEntry> days;

    public List<DayEntry> getDays() { return days; }
    public void setDays(List<DayEntry> days) { this.days = days; }

    public static class DayEntry {
        private String  dayOfWeek;  // e.g. "MONDAY"
        private boolean enabled;
        private String  startTime;  // "HH:mm"
        private String  endTime;    // "HH:mm"

        public String  getDayOfWeek()               { return dayOfWeek; }
        public void    setDayOfWeek(String d)        { this.dayOfWeek = d; }
        public boolean isEnabled()                  { return enabled; }
        public void    setEnabled(boolean enabled)   { this.enabled = enabled; }
        public String  getStartTime()               { return startTime; }
        public void    setStartTime(String t)        { this.startTime = t; }
        public String  getEndTime()                 { return endTime; }
        public void    setEndTime(String t)          { this.endTime = t; }
    }
}
