package com.calendly.clone.repository;

import com.calendly.clone.entity.AvailabilityRule;
import com.calendly.clone.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {
    List<AvailabilityRule> findByUserOrderByDayOfWeek(User user);

    @Transactional
    void deleteByUser(User user);
}
