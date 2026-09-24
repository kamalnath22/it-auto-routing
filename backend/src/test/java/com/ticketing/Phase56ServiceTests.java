package com.ticketing;

import com.ticketing.entity.Priority;
import com.ticketing.entity.SlaRule;
import com.ticketing.entity.SlaStatus;
import com.ticketing.entity.Team;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.repository.SlaRuleRepository;
import com.ticketing.repository.TeamRepository;
import com.ticketing.service.PriorityService;
import com.ticketing.service.RoutingService;
import com.ticketing.service.SlaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase56ServiceTests {
    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SlaRuleRepository slaRuleRepository;

    @Test
    void categoryRoutesToConfiguredTeam() {
        Team team = new Team("IT Access", "Access", Instant.now());
        when(teamRepository.findByCategoryAndActiveTrue("Access")).thenReturn(Optional.of(team));
        Ticket ticket = ticket("Access", TicketStatus.OPEN);

        new RoutingService(teamRepository).route(ticket);

        assertThat(ticket.getAssignedTeam()).isSameAs(team);
    }

    @Test
    void missingTeamLeavesTicketUnassigned() {
        when(teamRepository.findByCategoryAndActiveTrue("Miscellaneous")).thenReturn(Optional.empty());
        Ticket ticket = ticket("Miscellaneous", TicketStatus.OPEN);

        new RoutingService(teamRepository).route(ticket);

        assertThat(ticket.getAssignedTeam()).isNull();
    }

    @Test
    void priorityRulesAreDeterministic() {
        PriorityService service = new PriorityService();
        Ticket outage = ticket(null, TicketStatus.OPEN);
        outage.setSubject("Complete service outage");
        outage.setDescription("Everyone is blocked");
        Ticket minor = ticket(null, TicketStatus.OPEN);
        minor.setSubject("Minor issue");
        minor.setDescription("Non-urgent request");

        assertThat(service.determine(outage)).isEqualTo(Priority.CRITICAL);
        assertThat(service.determine(minor)).isEqualTo(Priority.LOW);
    }

    @Test
    void slaDeadlineAndStatusesUseUtcInstants() {
        SlaService service = new SlaService(slaRuleRepository);
        Instant created = Instant.parse("2026-09-24T10:00:00Z");
        when(slaRuleRepository.findByPriorityAndActiveTrue(Priority.HIGH))
                .thenReturn(Optional.of(new SlaRule(Priority.HIGH, 240)));
        Ticket ticket = ticket(null, TicketStatus.OPEN);
        ticket.setCreatedAt(created);
        ticket.setPriority(Priority.HIGH);

        service.apply(ticket, Instant.parse("2026-09-24T10:01:00Z"));
        assertThat(ticket.getSlaDeadline()).isEqualTo(Instant.parse("2026-09-24T14:00:00Z"));
        assertThat(ticket.getSlaStatus()).isEqualTo(SlaStatus.ON_TRACK);

        service.updateStatus(ticket, Instant.parse("2026-09-24T13:20:00Z"));
        assertThat(ticket.getSlaStatus()).isEqualTo(SlaStatus.AT_RISK);
        service.updateStatus(ticket, Instant.parse("2026-09-24T14:01:00Z"));
        assertThat(ticket.getSlaStatus()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void resolvedAndClosedTicketsHaveResolvedSlaStatus() {
        SlaService service = new SlaService(slaRuleRepository);
        Ticket ticket = ticket(null, TicketStatus.RESOLVED);
        ticket.setSlaDeadline(Instant.now());
        ticket.setSlaTargetMinutes(480);

        service.updateStatus(ticket, Instant.now());

        assertThat(ticket.getSlaStatus()).isEqualTo(SlaStatus.RESOLVED);
    }

    private Ticket ticket(String category, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setCategory(category);
        ticket.setStatus(status);
        ticket.setSubject("Ticket");
        ticket.setDescription("Details");
        ticket.setCreatedAt(Instant.now());
        return ticket;
    }
}
