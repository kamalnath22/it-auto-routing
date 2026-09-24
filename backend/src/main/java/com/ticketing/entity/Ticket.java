package com.ticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_sla_deadline", columnList = "sla_deadline"),
        @Index(name = "idx_ticket_sla_status", columnList = "sla_status")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "predicted_category", length = 80)
    private String predictedCategory;

    @Column(name = "ml_confidence")
    private Double mlConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_status", length = 30)
    private ClassificationStatus classificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    @Column(name = "sla_deadline")
    private Instant slaDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", length = 20)
    private SlaStatus slaStatus;

    @Column(name = "sla_target_minutes")
    private Integer slaTargetMinutes;

    @ManyToOne(fetch = FetchType.EAGER)
    // Keep the Phase 1 column name so existing tickets remain owned after the schema update.
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_agent")
    private User assignedAgent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_team")
    private Team assignedTeam;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public void setPredictedCategory(String predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public Double getMlConfidence() {
        return mlConfidence;
    }

    public void setMlConfidence(Double mlConfidence) {
        this.mlConfidence = mlConfidence;
    }

    public ClassificationStatus getClassificationStatus() {
        return classificationStatus;
    }

    public void setClassificationStatus(ClassificationStatus classificationStatus) {
        this.classificationStatus = classificationStatus;
    }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Instant getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(Instant slaDeadline) { this.slaDeadline = slaDeadline; }
    public SlaStatus getSlaStatus() { return slaStatus; }
    public void setSlaStatus(SlaStatus slaStatus) { this.slaStatus = slaStatus; }
    public Integer getSlaTargetMinutes() { return slaTargetMinutes; }
    public void setSlaTargetMinutes(Integer slaTargetMinutes) { this.slaTargetMinutes = slaTargetMinutes; }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getAssignedAgent() {
        return assignedAgent;
    }

    public void setAssignedAgent(User assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    public Team getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(Team assignedTeam) { this.assignedTeam = assignedTeam; }
}
