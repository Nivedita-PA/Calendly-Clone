package com.calendly.clone.service;


import com.calendly.clone.dto.ContactForm;
import com.calendly.clone.entity.Contact;
import com.calendly.clone.entity.User;
import com.calendly.clone.repository.ContactRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository){
        this.contactRepository = contactRepository;
    }

    public List<Contact> showContacts(User user){
        return contactRepository.findByOwner(user);
    }

    public Contact searchById(Long id){
        Optional<Contact> contact = contactRepository.findById(id);
        if(!contact.isEmpty()) return contact.get();
        else return null;
    }

    public boolean searchByUserAndEmail(User user, String email){
        return contactRepository.existsByOwnerAndEmail(user,email);
    }

    public Contact updateContact(Long id, ContactForm form, User user) {

        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        // Security check: user can edit only own contacts
        if (!contact.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if(contactRepository.existsByOwnerAndEmail(user,contactRepository.findById(id).get().getEmail())) {
            contact.setName(form.getName().trim());
            contact.setEmail(form.getEmail().trim());
        }else throw new RuntimeException("Unauthorized");

        return contactRepository.save(contact);
    }

    public void removeContact(Long id){
        contactRepository.deleteById(id);
    }

    public void addContact(ContactForm form, User user) {
        Contact contact = new Contact();
        contact.setOwner(user);
        contact.setName(form.getName());
        contact.setEmail(form.getEmail());
        contactRepository.save(contact);
    }
}
