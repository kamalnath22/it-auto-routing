package com.ticketing.service;

import com.ticketing.dto.AssignTicketRequest;
import com.ticketing.dto.CreateTicketRequest;
import com.ticketing.dto.StatusUpdateRequest;
import com.ticketing.dto.TicketResponse;
import com.ticketing.entity.Role;
import com.ticketing.entity.ClassificationStatus;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.entity.User;
import com.ticketing.repository.TicketRepository;
import com.ticketing.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final java.util.Set<String> VALID_CATEGORIES = java.util.Set.of(
            "Hardware", "HR Support", "Access", "Miscellaneous", "Storage", "Purchase",
            "Internal Project", "Administrative rights");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final MlClassificationClient mlClassificationClient;
    private final double mlConfidenceThreshold;
    private final RoutingService routingService;
    private final PriorityService priorityService;
    private final SlaService slaService;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository,
                         MlClassificationClient mlClassificationClient,
                         @Value("${ml.confidence-threshold:0.80}") double mlConfidenceThreshold,
                         RoutingService routingService,
                         PriorityService priorityService,
                         SlaService slaService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.mlClassificationClient = mlClassificationClient;
        this.mlConfidenceThreshold = mlConfidenceThreshold;
        this.routingService = routingService;
        this.priorityService = priorityService;
        this.slaService = slaService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TicketResponse createTicket(CreateTicketRequest request, String userEmail) {
        User creator = findUser(userEmail);
        if (creator.getRole() != Role.EMPLOYEE) {
            throw new AccessDeniedException("Only employees can create tickets");
        }
        Instant now = Instant.now();
        Ticket ticket = new Ticket();
        ticket.setSubject(request.subject().trim());
        ticket.setDescription(request.description().trim());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setUser(creator);
        ticket.setClassificationStatus(ClassificationStatus.PENDING);
        Ticket saved = ticketRepository.save(ticket);
        classify(saved);
        return toResponse(saved);
    }

    public List<TicketResponse> getTicketsForUser(String userEmail, String keyword, TicketStatus status) {
        User user = findUser(userEmail);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Ticket> tickets = switch (user.getRole()) {
            case EMPLOYEE -> ticketRepository.findByOwnerFilters(user.getId(), normalizedKeyword, status);
            case AGENT -> ticketRepository.findByAgentFilters(user.getId(), normalizedKeyword, status);
            case ADMIN -> ticketRepository.findAllByFilters(normalizedKeyword, status);
        };
        return tickets.stream().map(this::toResponse).toList();
    }

    public TicketResponse getTicketById(Long id, String userEmail) {
        User user = findUser(userEmail);
        Ticket ticket = findTicket(id);
        checkCanView(ticket, user);
        return toResponse(ticket);
    }

    public List<TicketResponse> getReviewQueue(String userEmail) {
        User user = findUser(userEmail);
        if (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only agents and administrators can access the review queue");
        }
        return ticketRepository.findByClassificationStatusOrderByUpdatedAtDesc(ClassificationStatus.MANUAL_REVIEW)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TicketResponse manuallyClassify(Long id, String category, String userEmail) {
        User user = findUser(userEmail);
        if (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only agents and administrators can classify tickets");
        }
        if (!VALID_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unsupported ticket category");
        }
        Ticket ticket = findTicket(id);
        ticket.setCategory(category);
        ticket.setClassificationStatus(ClassificationStatus.MANUALLY_CLASSIFIED);
        routingService.route(ticket);
        applyPriorityAndSla(ticket);
        ticket.setUpdatedAt(Instant.now());
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse updateStatus(Long id, StatusUpdateRequest request, String userEmail) {
        User user = findUser(userEmail);
        Ticket ticket = findTicket(id);
        checkCanView(ticket, user);
        if (user.getRole() == Role.EMPLOYEE && request.status() != TicketStatus.CLOSED) {
            throw new AccessDeniedException("Employees can only close resolved tickets");
        }
        if (user.getRole() == Role.AGENT
                && request.status() != TicketStatus.IN_PROGRESS
                && request.status() != TicketStatus.RESOLVED) {
            throw new AccessDeniedException("Agents can only move assigned tickets to in progress or resolved");
        }
        validateTransition(ticket.getStatus(), request.status());
        ticket.setStatus(request.status());
        ticket.setUpdatedAt(Instant.now());
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse assignTicket(Long id, AssignTicketRequest request, String userEmail) {
        requireRole(userEmail, Role.ADMIN);
        Ticket ticket = findTicket(id);
        User agent = userRepository.findById(request.agentId())
                .orElseThrow(() -> new NoSuchElementException("Agent not found"));
        if (agent.getRole() != Role.AGENT) {
            throw new IllegalArgumentException("Tickets can only be assigned to AGENT users");
        }
        ticket.setAssignedAgent(agent);
        ticket.setUpdatedAt(Instant.now());
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse unassignTicket(Long id, String userEmail) {
        requireRole(userEmail, Role.ADMIN);
        Ticket ticket = findTicket(id);
        ticket.setAssignedAgent(null);
        ticket.setUpdatedAt(Instant.now());
        return toResponse(ticketRepository.save(ticket));
    }

    private void validateTransition(TicketStatus current, TicketStatus next) {
        if (current == null || next == null || next.ordinal() != current.ordinal() + 1) {
            throw new IllegalArgumentException("Invalid ticket status transition from " + current + " to " + next);
        }
    }

    private void classify(Ticket ticket) {
        try {
            var prediction = mlClassificationClient.predict(ticket.getSubject() + "\n" + ticket.getDescription());
            ticket.setPredictedCategory(prediction.category());
            ticket.setMlConfidence(prediction.confidence());
            if (prediction.confidence() >= mlConfidenceThreshold) {
                ticket.setCategory(prediction.category());
                ticket.setClassificationStatus(ClassificationStatus.AUTO_CLASSIFIED);
                routingService.route(ticket);
            } else {
                ticket.setClassificationStatus(ClassificationStatus.MANUAL_REVIEW);
                ticket.setAssignedTeam(null);
            }
        } catch (RuntimeException exception) {
            log.warn("Ticket {} could not be classified; sending to manual review: {}", ticket.getId(), exception.getMessage());
            ticket.setClassificationStatus(ClassificationStatus.MANUAL_REVIEW);
            ticket.setAssignedTeam(null);
        }
        ticket.setUpdatedAt(Instant.now());
        applyPriorityAndSla(ticket);
        ticketRepository.save(ticket);
    }

    private void applyPriorityAndSla(Ticket ticket) {
        ticket.setPriority(priorityService.determine(ticket));
        slaService.apply(ticket, Instant.now());
    }

    private void checkCanView(Ticket ticket, User user) {
        boolean allowed = switch (user.getRole()) {
            case ADMIN -> true;
            case EMPLOYEE -> ticket.getUser() != null && ticket.getUser().getId().equals(user.getId());
            case AGENT -> ticket.getAssignedAgent() != null && ticket.getAssignedAgent().getId().equals(user.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("You do not have permission to access this ticket");
        }
    }

    private void requireRole(String email, Role role) {
        if (findUser(email).getRole() != role) {
            throw new AccessDeniedException("Only administrators can manage ticket assignments");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    private Ticket findTicket(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Ticket not found"));
    }

    private TicketResponse toResponse(Ticket ticket) {
        User owner = ticket.getUser();
        User agent = ticket.getAssignedAgent();
        var team = ticket.getAssignedTeam();
        return new TicketResponse(
                ticket.getId(), ticket.getSubject(), ticket.getDescription(), ticket.getStatus(),
                ticket.getCreatedAt(), owner == null ? null : owner.getId(), owner == null ? null : owner.getEmail(),
                ticket.getUpdatedAt(), agent == null ? null : agent.getId(), agent == null ? null : agent.getEmail(),
                ticket.getCategory(), ticket.getPredictedCategory(), ticket.getMlConfidence(), ticket.getClassificationStatus(),
                team == null ? null : team.getId(), team == null ? null : team.getName(), ticket.getPriority(),
                ticket.getSlaDeadline(), ticket.getSlaStatus(), ticket.getSlaTargetMinutes()
        );
    }
}
