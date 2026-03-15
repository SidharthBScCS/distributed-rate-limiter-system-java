package com.system.ratelimiter.repository;

import com.system.ratelimiter.entity.RequestStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequestStatsRepository extends JpaRepository<RequestStats, Long> {
    Optional<RequestStats> findTopByOrderByIdAsc();
}
