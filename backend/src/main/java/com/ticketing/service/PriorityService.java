package com.ticketing.service;

import com.ticketing.entity.Priority;
import com.ticketing.entity.Ticket;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PriorityService {
    public Priority determine(Ticket ticket) {
        String text = (ticket.getSubject() + " " + ticket.getDescription() + " "
                + (ticket.getCategory() == null ? "" : ticket.getCategory())).toLowerCase(Locale.ROOT);
        if (containsAny(text, "complete service outage", "security incident", "production-wide", "emergency")) {
            return Priority.CRITICAL;
        }
        if (containsAny(text, "service unavailable", "multiple users", "major access", "all users")) {
            return Priority.HIGH;
        }
        if (containsAny(text, "informational", "minor issue", "non-urgent", "non urgent")) {
            return Priority.LOW;
        }
        return Priority.MEDIUM;
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }
}
