package com.ticketing.controller;

import com.ticketing.dto.CreateTicketRequest;
import com.ticketing.dto.TicketResponse;
import com.ticketing.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin(origins = {"http://localhost:5500", "http://127.0.0.1:5500"})
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request, Principal principal) {
        return ticketService.createTicket(request, principal.getName());
    }

    @GetMapping
    public List<TicketResponse> getAllTickets(Principal principal) {
        return ticketService.getTicketsForUser(principal.getName());
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id, Principal principal) {
        return ticketService.getTicketById(id, principal.getName());
    }
}
