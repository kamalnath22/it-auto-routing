package com.ticketing.repository;

import com.ticketing.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByCategoryAndActiveTrue(String category);
    List<Team> findByActiveTrueOrderByNameAsc();
}
