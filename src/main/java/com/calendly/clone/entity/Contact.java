package com.calendly.clone.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "contacts")
@Getter
@Setter
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @NotBlank
    @Column(name = "guest_name", nullable = false, length = 100)
    private String name;

    @NotBlank
    @Email
    @Column(name = "guest_email", nullable = false, length = 100)
    private String email;

    @Transient
    private LocalDateTime upcomingBooking;

    @Transient
    private LocalDateTime lastBooking;
}
