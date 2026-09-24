package com.ticketing.service;

import com.ticketing.entity.Priority;
import com.ticketing.entity.SlaRule;
import com.ticketing.entity.SlaStatus;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.repository.SlaRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class SlaService {
    private static final Logger log = LoggerFactory.getLogger(SlaService.class);
    private final SlaRuleRepository slaRuleRepository;

    public SlaService(SlaRuleRepository slaRuleRepository) {
        this.slaRuleRepository = slaRuleRepository;
    }

    public void apply(Ticket ticket, Instant now) {
        Priority priority = ticket.getPriority();
        if (priority == null) return;
        SlaRule rule = slaRuleRepository.findByPriorityAndActiveTrue(priority).orElse(null);
        if (rule == null) {
            log.warn("No active SLA rule configured for priority {} on ticket {}", priority, ticket.getId());
            return;
        }
        boolean targetChanged = ticket.getSlaTargetMinutes() == null
                || ticket.getSlaTargetMinutes() != rule.getTargetMinutes();
        ticket.setSlaTargetMinutes(rule.getTargetMinutes());
        if (ticket.getCreatedAt() != null && (ticket.getSlaDeadline() == null || targetChanged)) {
            ticket.setSlaDeadline(ticket.getCreatedAt().plusSeconds(rule.getTargetMinutes() * 60L));
        }
        updateStatus(ticket, now);
    }

    public boolean updateStatus(Ticket ticket, Instant now) {
        SlaStatus previous = ticket.getSlaStatus();
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            ticket.setSlaStatus(SlaStatus.RESOLVED);
        } else if (ticket.getSlaDeadline() == null || ticket.getSlaTargetMinutes() == null) {
            return false;
        } else if (!now.isBefore(ticket.getSlaDeadline())) {
            ticket.setSlaStatus(SlaStatus.BREACHED);
        } else {
            long totalSeconds = ticket.getSlaTargetMinutes() * 60L;
            long remainingSeconds = Duration.between(now, ticket.getSlaDeadline()).getSeconds();
            ticket.setSlaStatus(remainingSeconds <= totalSeconds * 0.20 ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK);
        }
        return previous != ticket.getSlaStatus();
    }
}
