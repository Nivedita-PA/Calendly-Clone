package com.calendly.clone.service;

import com.calendly.clone.entity.Booking;
import com.calendly.clone.entity.enums.BookingStatus;
import com.calendly.clone.repository.BookingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingScheduler {

    private final BookingRepository bookingRepository;

    public BookingScheduler(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedRate = 60000) // every 1 minute
    public void markCompletedBookings() {

        List<Booking> bookings =
                bookingRepository.findByStatus(BookingStatus.CONFIRMED);

        for (Booking b : bookings) {
            if (b.getEndTime().isBefore(LocalDateTime.now())) {
                b.setStatus(BookingStatus.COMPLETED);
            }
        }

        bookingRepository.saveAll(bookings);
    }
}
