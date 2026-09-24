package com.ticketing.controller;

import com.ticketing.dto.AssignTicketRequest;
import com.ticketing.dto.ClassificationRequest;
import com.ticketing.dto.CreateTicketRequest;
import com.ticketing.dto.StatusUpdateRequest;
import com.ticketing.dto.TicketResponse;
import com.ticketing.entity.TicketStatus;
import com.ticketing.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<TicketResponse> getAllTickets(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) TicketStatus status,
            Principal principal) {
        return ticketService.getTicketsForUser(principal.getName(), keyword, status);
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id, Principal principal) {
        return ticketService.getTicketById(id, principal.getName());
    }

    @GetMapping("/review-queue")
    public List<TicketResponse> getReviewQueue(Principal principal) {
        return ticketService.getReviewQueue(principal.getName());
    }

    @PatchMapping("/{id}/classification")
    public TicketResponse classifyTicket(@PathVariable Long id,
                                         @Valid @RequestBody ClassificationRequest request,
                                         Principal principal) {
        return ticketService.manuallyClassify(id, request.category().trim(), principal.getName());
    }

    @PatchMapping("/{id}/status")
    public TicketResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request,
                                       Principal principal) {
        return ticketService.updateStatus(id, request, principal.getName());
    }

    @PatchMapping("/{id}/assign")
    public TicketResponse assignTicket(@PathVariable Long id, @Valid @RequestBody AssignTicketRequest request,
                                       Principal principal) {
        return ticketService.assignTicket(id, request, principal.getName());
    }

    @PatchMapping("/{id}/unassign")
    public TicketResponse unassignTicket(@PathVariable Long id, Principal principal) {
        return ticketService.unassignTicket(id, principal.getName());
    }
}
