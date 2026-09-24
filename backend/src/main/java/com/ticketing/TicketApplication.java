package com.ticketing;

import com.ticketing.entity.Role;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.entity.Priority;
import com.ticketing.entity.SlaRule;
import com.ticketing.entity.Team;
import com.ticketing.entity.User;
import com.ticketing.repository.TicketRepository;
import com.ticketing.repository.SlaRuleRepository;
import com.ticketing.repository.TeamRepository;
import com.ticketing.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository,
                                      TicketRepository ticketRepository,
                                      TeamRepository teamRepository,
                                      SlaRuleRepository slaRuleRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            seedTeams(teamRepository);
            seedSlaRules(slaRuleRepository);
            if (userRepository.findByEmail("admin@example.com").isEmpty()) {
                userRepository.save(new User(
                        "Admin User",
                        "admin@example.com",
                        passwordEncoder.encode("Admin123"),
                        Role.ADMIN,
                        Instant.now()
                ));
            }

            if (userRepository.findByEmail("agent@example.com").isEmpty()) {
                userRepository.save(new User(
                        "Agent User",
                        "agent@example.com",
                        passwordEncoder.encode("Agent123"),
                        Role.AGENT,
                        Instant.now()
                ));
            }

            User defaultEmployee = userRepository.findByEmail("employee@example.com").orElseGet(() ->
                    userRepository.save(new User(
                            "Default Employee",
                            "employee@example.com",
                            passwordEncoder.encode("Employee123"),
                            Role.EMPLOYEE,
                            Instant.now()
                    ))
            );

            // Backward compatibility for existing Phase 1 tickets and timestamps.
            for (Ticket ticket : ticketRepository.findAll()) {
                if (ticket.getUser() == null) {
                    ticket.setUser(defaultEmployee);
                }
                if (ticket.getStatus() == null) {
                    ticket.setStatus(TicketStatus.OPEN);
                }
                if (ticket.getUpdatedAt() == null) {
                    ticket.setUpdatedAt(ticket.getCreatedAt() == null ? Instant.now() : ticket.getCreatedAt());
                }
                ticketRepository.save(ticket);
            }
        };
    }

    private void seedTeams(TeamRepository repository) {
        record TeamSeed(String name, String category) {}
        for (TeamSeed seed : List.of(
                new TeamSeed("Hardware Support", "Hardware"),
                new TeamSeed("IT Access", "Access"),
                new TeamSeed("HR Helpdesk", "HR Support"),
                new TeamSeed("Procurement", "Purchase"),
                new TeamSeed("Storage/Infrastructure", "Storage"),
                new TeamSeed("Internal Projects", "Internal Project"),
                new TeamSeed("Administrative Support", "Administrative rights"),
                new TeamSeed("General Support", "Miscellaneous"))) {
            if (repository.findByCategoryAndActiveTrue(seed.category()).isEmpty()) {
                repository.save(new Team(seed.name(), seed.category(), Instant.now()));
            }
        }
    }

    private void seedSlaRules(SlaRuleRepository repository) {
        for (var entry : java.util.Map.of(
                Priority.CRITICAL, 120,
                Priority.HIGH, 240,
                Priority.MEDIUM, 480,
                Priority.LOW, 1440).entrySet()) {
            if (repository.findByPriorityAndActiveTrue(entry.getKey()).isEmpty()) {
                repository.save(new SlaRule(entry.getKey(), entry.getValue()));
            }
        }
    }
}
