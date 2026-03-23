package com.system.ratelimiter.repository;

import com.system.ratelimiter.entity.RequestStats;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RequestStatsRepository extends JpaRepository<RequestStats, Long> {
    Optional<RequestStats> findTopByOrderByIdAsc();

    @Modifying
    @Query("""
            update RequestStats stats
            set stats.totalRequests = stats.totalRequests + :totalDelta,
                stats.allowedRequests = stats.allowedRequests + :allowedDelta,
                stats.blockedRequests = stats.blockedRequests + :blockedDelta
            where stats.id = :id
            """)
    int incrementCounters(
            @Param("id") Long id,
            @Param("totalDelta") long totalDelta,
            @Param("allowedDelta") long allowedDelta,
            @Param("blockedDelta") long blockedDelta
    );
}
