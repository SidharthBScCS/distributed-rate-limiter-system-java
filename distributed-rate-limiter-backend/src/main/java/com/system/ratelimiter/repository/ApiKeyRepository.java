package com.system.ratelimiter.repository;

import com.system.ratelimiter.entity.ApiKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByApiKey(String apiKey);

    @Query("""
            select apiKey from ApiKey apiKey
            where lower(apiKey.userName) not like 'demo%'
              and lower(apiKey.userName) not like 'sample%'
              and lower(apiKey.apiKey) not like 'demo%'
              and lower(apiKey.apiKey) not like 'sample%'
            """)
    List<ApiKey> findAllRealKeys();

    @Query("""
            select apiKey from ApiKey apiKey
            where (
                :search = ''
                or lower(apiKey.userName) like concat('%', :search, '%')
                or lower(apiKey.apiKey) like concat('%', :search, '%')
            )
            and lower(apiKey.userName) not like 'demo%'
            and lower(apiKey.userName) not like 'sample%'
            and lower(apiKey.apiKey) not like 'demo%'
            and lower(apiKey.apiKey) not like 'sample%'
            """)
    Page<ApiKey> findDashboardKeys(@Param("search") String search, Pageable pageable);

    @Query("""
            select apiKey from ApiKey apiKey
            where lower(apiKey.userName) not like 'demo%'
              and lower(apiKey.userName) not like 'sample%'
              and lower(apiKey.apiKey) not like 'demo%'
              and lower(apiKey.apiKey) not like 'sample%'
            order by apiKey.totalRequests desc
            """)
    Page<ApiKey> findTopKeys(Pageable pageable);

    @Modifying
    @Query("""
            update ApiKey apiKey
            set apiKey.totalRequests = apiKey.totalRequests + :totalDelta,
                apiKey.allowedRequests = apiKey.allowedRequests + :allowedDelta,
                apiKey.blockedRequests = apiKey.blockedRequests + :blockedDelta,
                apiKey.status = :status
            where apiKey.apiKey = :apiKeyValue
            """)
    int incrementCountersAndStatus(
            @Param("apiKeyValue") String apiKeyValue,
            @Param("totalDelta") long totalDelta,
            @Param("allowedDelta") long allowedDelta,
            @Param("blockedDelta") long blockedDelta,
            @Param("status") String status
    );
}
