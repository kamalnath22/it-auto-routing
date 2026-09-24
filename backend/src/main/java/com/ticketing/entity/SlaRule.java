package com.ticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sla_rules")
public class SlaRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private Priority priority;

    @Column(name = "target_minutes", nullable = false)
    private int targetMinutes;

    @Column(nullable = false)
    private boolean active = true;

    public SlaRule() {}

    public SlaRule(Priority priority, int targetMinutes) {
        this.priority = priority;
        this.targetMinutes = targetMinutes;
    }

    public Long getId() { return id; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public int getTargetMinutes() { return targetMinutes; }
    public void setTargetMinutes(int targetMinutes) { this.targetMinutes = targetMinutes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
