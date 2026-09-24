package com.ticketing;

import com.ticketing.entity.Role;
import com.ticketing.entity.Ticket;
import com.ticketing.entity.User;
import com.ticketing.repository.TicketRepository;
import com.ticketing.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

@SpringBootApplication
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository,
                                      TicketRepository ticketRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
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

            // Backward compatibility for existing Phase 1 tickets
            List<Ticket> unownedTickets = ticketRepository.findAll()
                    .stream()
                    .filter(t -> t.getUser() == null)
                    .toList();

            for (Ticket ticket : unownedTickets) {
                ticket.setUser(defaultEmployee);
                ticketRepository.save(ticket);
            }
        };
    }
}
