package com.ticketing;

import com.ticketing.dto.AssignTicketRequest;
import com.ticketing.dto.CreateTicketRequest;
import com.ticketing.dto.MlPredictionResponse;
import com.ticketing.dto.StatusUpdateRequest;
import com.ticketing.entity.Role;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.entity.User;
import com.ticketing.repository.TicketRepository;
import com.ticketing.repository.UserRepository;
import com.ticketing.service.TicketService;
import com.ticketing.service.MlClassificationClient;
import com.ticketing.service.RoutingService;
import com.ticketing.service.PriorityService;
import com.ticketing.service.SlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TicketServicePhase3Tests {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MlClassificationClient mlClassificationClient;

    @Mock
    private RoutingService routingService;

    @Mock
    private PriorityService priorityService;

    @Mock
    private SlaService slaService;

    private TicketService ticketService;
    private User employee;
    private User agent;
    private User admin;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, userRepository, mlClassificationClient, 0.80,
                routingService, priorityService, slaService);
        employee = user(1L, "employee@example.com", Role.EMPLOYEE);
        agent = user(2L, "agent@example.com", Role.AGENT);
        admin = user(3L, "admin@example.com", Role.ADMIN);
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void employeeCanCloseOwnResolvedTicket() {
        Ticket ticket = ticket(10L, employee, null, TicketStatus.RESOLVED);
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.updateStatus(10L, new StatusUpdateRequest(TicketStatus.CLOSED), employee.getEmail());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getUpdatedAt()).isNotNull();
    }

    @Test
    void invalidTransitionIsRejectedByService() {
        Ticket ticket = ticket(11L, employee, null, TicketStatus.OPEN);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(11L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.updateStatus(11L,
                new StatusUpdateRequest(TicketStatus.RESOLVED), admin.getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentCannotReadUnassignedTicket() {
        Ticket ticket = ticket(12L, employee, null, TicketStatus.OPEN);
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(ticketRepository.findById(12L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getTicketById(12L, agent.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanAssignAgentButNotEmployee() {
        Ticket ticket = ticket(13L, employee, null, TicketStatus.OPEN);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(13L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(userRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        ticketService.assignTicket(13L, new AssignTicketRequest(agent.getId()), admin.getEmail());
        assertThat(ticket.getAssignedAgent()).isSameAs(agent);

        assertThatThrownBy(() -> ticketService.assignTicket(13L,
                new AssignTicketRequest(employee.getId()), admin.getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void highConfidencePredictionIsAutoClassified() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(mlClassificationClient.predict(any())).thenReturn(new MlPredictionResponse("Access", 0.91));

        TicketService service = ticketService;
        var response = service.createTicket(new CreateTicketRequest("VPN issue", "Cannot connect"), employee.getEmail());

        assertThat(response.category()).isEqualTo("Access");
        assertThat(response.classificationStatus().name()).isEqualTo("AUTO_CLASSIFIED");
    }

    @Test
    void mlFailureSendsTicketToManualReview() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(mlClassificationClient.predict(any())).thenThrow(new RuntimeException("service unavailable"));

        var response = ticketService.createTicket(new CreateTicketRequest("VPN issue", "Cannot connect"), employee.getEmail());

        assertThat(response.category()).isNull();
        assertThat(response.classificationStatus().name()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void lowConfidencePredictionKeepsFinalCategoryUnset() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(mlClassificationClient.predict(any())).thenReturn(new MlPredictionResponse("Access", 0.79));

        var response = ticketService.createTicket(new CreateTicketRequest("VPN issue", "Cannot connect"), employee.getEmail());

        assertThat(response.category()).isNull();
        assertThat(response.predictedCategory()).isEqualTo("Access");
        assertThat(response.classificationStatus().name()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void agentCanManuallyClassifyAndEmployeeCannot() {
        Ticket ticket = ticket(14L, employee, null, TicketStatus.OPEN);
        when(ticketRepository.findById(14L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));

        var response = ticketService.manuallyClassify(14L, "Hardware", agent.getEmail());

        assertThat(response.category()).isEqualTo("Hardware");
        assertThat(response.classificationStatus().name()).isEqualTo("MANUALLY_CLASSIFIED");
        assertThatThrownBy(() -> ticketService.manuallyClassify(14L, "Access", employee.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User user(Long id, String email, Role role) {
        User user = new User("Test User", email, "hash", role, Instant.now());
        user.setId(id);
        return user;
    }

    private Ticket ticket(Long id, User owner, User assignedAgent, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setUser(owner);
        ticket.setAssignedAgent(assignedAgent);
        ticket.setStatus(status);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(ticket.getCreatedAt());
        return ticket;
    }
}
