package com.ticketing.controller;

import com.ticketing.dto.TeamResponse;
import com.ticketing.service.RoutingService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = {"http://localhost:5500", "http://127.0.0.1:5500"})
public class TeamController {
    private final RoutingService routingService;

    public TeamController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping
    public List<TeamResponse> getTeams() {
        return routingService.listTeams();
    }

    @GetMapping("/{id}")
    public TeamResponse getTeam(@PathVariable Long id) {
        return routingService.getTeam(id);
    }
}
