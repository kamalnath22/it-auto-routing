package com.ticketing.repository;

import com.ticketing.entity.Priority;
import com.ticketing.entity.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaRuleRepository extends JpaRepository<SlaRule, Long> {
    Optional<SlaRule> findByPriorityAndActiveTrue(Priority priority);
}
