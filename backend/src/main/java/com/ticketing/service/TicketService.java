package com.ticketing.service;

import com.ticketing.dto.CreateTicketRequest;
import com.ticketing.dto.TicketResponse;
import com.ticketing.entity.Role;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.User;
import com.ticketing.repository.TicketRepository;
import com.ticketing.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private static final String OPEN_STATUS = "OPEN";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found with email: " + userEmail));

        Ticket ticket = new Ticket();
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setStatus(OPEN_STATUS);
        ticket.setCreatedAt(Instant.now());
        ticket.setUser(user);

        return toResponse(ticketRepository.save(ticket));
    }

    public List<TicketResponse> getTicketsForUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found with email: " + userEmail));

        if (user.getRole() == Role.EMPLOYEE) {
            return ticketRepository.findByUserId(user.getId())
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        // AGENT and ADMIN can view all tickets in system
        return ticketRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TicketResponse getTicketById(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found with email: " + userEmail));

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found with id " + id));

        // Employee ownership check: employees can only view their own tickets
        if (user.getRole() == Role.EMPLOYEE) {
            if (ticket.getUser() == null || !ticket.getUser().getId().equals(user.getId())) {
                throw new AccessDeniedException("Forbidden: You do not have permission to access this ticket");
            }
        }

        return toResponse(ticket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        Long userId = ticket.getUser() != null ? ticket.getUser().getId() : null;
        String userEmail = ticket.getUser() != null ? ticket.getUser().getEmail() : null;

        return new TicketResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                userId,
                userEmail
        );
    }
}
