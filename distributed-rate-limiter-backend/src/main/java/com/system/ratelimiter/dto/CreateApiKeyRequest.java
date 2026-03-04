package com.system.ratelimiter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CreateApiKeyRequest {

    @NotBlank(message = "userName is required")
    private String userName;

    @NotNull(message = "rateLimit is required")
    @Min(value = 1, message = "rateLimit must be >= 1")
    private Integer rateLimit;

    @NotNull(message = "windowSeconds is required")
    @Min(value = 1, message = "windowSeconds must be >= 1")
    private Integer windowSeconds;

    @Pattern(
            regexp = "SLIDING_WINDOW",
            message = "Algorithm must be SLIDING_WINDOW"
    )
    private String algorithm;

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
}
