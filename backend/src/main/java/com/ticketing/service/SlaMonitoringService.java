package com.ticketing.service;

import com.ticketing.entity.SlaStatus;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.repository.TicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SlaMonitoringService {
    private final TicketRepository ticketRepository;
    private final SlaService slaService;

    public SlaMonitoringService(TicketRepository ticketRepository, SlaService slaService) {
        this.ticketRepository = ticketRepository;
        this.slaService = slaService;
    }

    @Scheduled(fixedDelayString = "${sla.monitor-delay-ms:60000}")
    @Transactional
    public void monitor() {
        Instant now = Instant.now();
        for (Ticket ticket : ticketRepository.findBySlaDeadlineNotNullAndStatusNotIn(
                java.util.List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED))) {
            if (slaService.updateStatus(ticket, now)) {
                ticketRepository.save(ticket);
            }
        }
    }
}
