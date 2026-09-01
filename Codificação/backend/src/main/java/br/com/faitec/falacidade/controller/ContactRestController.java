package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.ContactMessage;
import br.com.faitec.falacidade.domain.dto.contact.ContactMessageDto;
import br.com.faitec.falacidade.port.service.contact.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactRestController {

    private final ContactService contactService;

    public ContactRestController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<Void> sendMessage(@Valid @RequestBody ContactMessageDto dto) {
        ContactMessage msg = new ContactMessage();
        msg.setName(dto.getName());   msg.setEmail(dto.getEmail());
        msg.setSubject(dto.getSubject()); msg.setMessage(dto.getMessage());
        contactService.save(msg);
        return ResponseEntity.ok().build();
    }
}
