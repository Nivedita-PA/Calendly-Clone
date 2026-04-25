package com.calendly.clone.repository;
import com.calendly.clone.entity.Contact;
import com.calendly.clone.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    Contact save(Contact contact);

    boolean existsByOwnerAndEmail(User owner,String email);

    List<Contact> findByOwner(User owner);

    Optional<Contact> findById(Long id);

    void deleteById(Long aLong);
}
