package com.ticketing.service;

import com.ticketing.entity.Team;
import com.ticketing.entity.Ticket;
import com.ticketing.dto.TeamResponse;
import com.ticketing.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class RoutingService {
    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private final TeamRepository teamRepository;

    public RoutingService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Optional<Team> route(Ticket ticket) {
        if (ticket.getCategory() == null || ticket.getCategory().isBlank()) {
            ticket.setAssignedTeam(null);
            return Optional.empty();
        }
        Optional<Team> team = teamRepository.findByCategoryAndActiveTrue(ticket.getCategory());
        if (team.isEmpty()) {
            log.warn("No active team configured for category {} on ticket {}", ticket.getCategory(), ticket.getId());
            ticket.setAssignedTeam(null);
        } else {
            ticket.setAssignedTeam(team.get());
        }
        return team;
    }

    public List<TeamResponse> listTeams() {
        return teamRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public TeamResponse getTeam(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Team not found"));
        return toResponse(team);
    }

    private TeamResponse toResponse(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getCategory(), team.isActive(), team.getCreatedAt());
    }
}
