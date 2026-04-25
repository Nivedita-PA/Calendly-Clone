package com.calendly.clone.dto;

import com.calendly.clone.entity.enums.MeetingType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class EventTypeForm {

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must be under 100 characters")
    private String title;

    @Size(max = 500, message = "Description must be under 500 characters")
    private String description;

    @Min(value = 5, message = "Duration must be at least 5 minutes")
    private int durationMinutes = 30;

    @Min(value = 0, message = "Buffer cannot be negative")
    private int bufferBefore = 0;

    @Min(value = 0, message = "Buffer cannot be negative")
    private int bufferAfter = 0;

    @NotNull(message = "Please select a meeting type")
    private MeetingType meetingType = MeetingType.ONE_ON_ONE;

    private String color = "#0069ff";

    private String meetingLink;

    public String getTitle()                           { return title; }
    public void setTitle(String title)                 { this.title = title; }
    public String getDescription()                     { return description; }
    public void setDescription(String description)     { this.description = description; }
    public int getDurationMinutes()                    { return durationMinutes; }
    public void setDurationMinutes(int d)              { this.durationMinutes = d; }
    public int getBufferBefore()                       { return bufferBefore; }
    public void setBufferBefore(int b)                 { this.bufferBefore = b; }
    public int getBufferAfter()                        { return bufferAfter; }
    public void setBufferAfter(int b)                  { this.bufferAfter = b; }
    public MeetingType getMeetingType()                { return meetingType; }
    public void setMeetingType(MeetingType t)          { this.meetingType = t; }
    public String getColor()                           { return color; }
    public void setColor(String color)                 { this.color = color; }
    public String getMeetingLink()                     { return meetingLink; }
    public void setMeetingLink(String meetingLink)     { this.meetingLink = meetingLink; }
}
