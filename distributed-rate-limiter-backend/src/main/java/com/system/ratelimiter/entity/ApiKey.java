package com.system.ratelimiter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(nullable = false)
    private Integer rateLimit;

    @Column(nullable = false)
    private Integer windowSeconds;

    @Column(nullable = false, columnDefinition = "varchar(30) default 'SLIDING_WINDOW'")
    private String algorithm;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(nullable = false, columnDefinition = "varchar(20) default 'Normal'")
    private String status;

    @Column(name = "total_request", nullable = false, columnDefinition = "bigint default 0")
    private Long totalRequests;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long allowedRequests;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long blockedRequests;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Integer rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Integer getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(Integer windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(Long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public Long getAllowedRequests() {
        return allowedRequests;
    }

    public void setAllowedRequests(Long allowedRequests) {
        this.allowedRequests = allowedRequests;
    }

    public Long getBlockedRequests() {
        return blockedRequests;
    }

    public void setBlockedRequests(Long blockedRequests) {
        this.blockedRequests = blockedRequests;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
