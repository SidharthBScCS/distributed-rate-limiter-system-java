package com.system.ratelimiter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateApiKeyRequest {

    @NotBlank(message = "userName is required")
    private String userName;

    @NotNull(message = "rateLimit is required")
    @Min(value = 1, message = "rateLimit must be >= 1")
    private Integer rateLimit;

    @NotNull(message = "windowSeconds is required")
    @Min(value = 1, message = "windowSeconds must be >= 1")
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
