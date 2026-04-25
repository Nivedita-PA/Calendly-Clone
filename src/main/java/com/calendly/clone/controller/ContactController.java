package com.calendly.clone.controller;

import com.calendly.clone.dto.ContactForm;
import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.Contact;
import com.calendly.clone.entity.User;
import com.calendly.clone.entity.enums.BookingStatus;
import com.calendly.clone.repository.EventTypeRepository;
import com.calendly.clone.repository.UserRepository;
import com.calendly.clone.service.BookingService;
import com.calendly.clone.service.ContactService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class ContactController {

    private final ContactService contactService;
    private final UserRepository userRepo;
    private final BookingService bookingService;
    private final EventTypeRepository eventTypeRepository;

    public ContactController(ContactService contactService,
                             UserRepository userRepo,
                             BookingService bookingService, EventTypeRepository eventTypeRepository){
        this.contactService = contactService;
        this.userRepo = userRepo;
        this.bookingService = bookingService;
        this.eventTypeRepository = eventTypeRepository;
    }

    @GetMapping("/contacts")
    public String showContacts(@AuthenticationPrincipal UserDetails principal,
                               Model model){
        User user = resolveUser(principal);
        List<Contact> contacts = contactService.showContacts(user);
        for (Contact contact : contacts) {
            contact.setUpcomingBooking(findUpcomingForContact(user, contact));
            contact.setLastBooking(findLastForContact(user, contact));
        }
        model.addAttribute("contacts", contacts);
        model.addAttribute("user", user);
        model.addAttribute("eventTypes", eventTypeRepository.findByOwner(user));
        return "contacts";
    }

    @GetMapping("/contacts/add")
    public String addPage(){
        return "add-contact";
    }

    @PostMapping("/contacts/add")
    public String addContact(@ModelAttribute ContactForm form,
                             @AuthenticationPrincipal UserDetails user){
        contactService.addContact(form, resolveUser(user));
        return "redirect:/contacts";
    }

    @GetMapping("/contacts/{id}/edit")
    public String showEditPage(@PathVariable Long id,
                               Model model){
        model.addAttribute("contact", contactService.searchById(id));
        return "edit-contact";
    }
    @PostMapping("/contacts/{id}/edit")
    public String editContact(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails principal,
                              @ModelAttribute ContactForm form){
        User user = resolveUser(principal);
        contactService.updateContact(id,form,user);
        return "redirect:/contacts";
    }

    @PostMapping("/remove/{id}")
    public String deleteContact(@PathVariable Long id){
        contactService.removeContact(id);
        return "redirect:/contacts";
    }
    private User resolveUser(UserDetails principal) {
        return userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private LocalDateTime findUpcomingForContact(User user, Contact contact) {

        List<Booking> bookings = bookingService.getAllBookings(user);

        return bookings.stream()
                .filter(b ->
                        b.getStatus() == BookingStatus.CONFIRMED &&
                                b.getStartTime().isAfter(LocalDateTime.now()) &&
                                b.getGuestEmail().equalsIgnoreCase(contact.getEmail())
                )
                .map(Booking::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private LocalDateTime findLastForContact(User user, Contact contact) {

        List<Booking> bookings = bookingService.getAllBookings(user);

        return bookings.stream()
                .filter(b ->
                        b.getStatus() == BookingStatus.COMPLETED &&
                                b.getGuestEmail().equalsIgnoreCase(contact.getEmail())
                )
                .map(Booking::getStartTime)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}
