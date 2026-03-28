package com.system.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateApiKeyRequest {

    @NotBlank(message = "userName is required")
    private String userName;

    @NotNull(message = "rateLimit is required")
    @Positive(message = "rateLimit must be >= 1")
    private Integer rateLimit;

    @NotNull(message = "windowSeconds is required")
    @Positive(message = "windowSeconds must be >= 1")
    private Integer windowSeconds;

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

}
