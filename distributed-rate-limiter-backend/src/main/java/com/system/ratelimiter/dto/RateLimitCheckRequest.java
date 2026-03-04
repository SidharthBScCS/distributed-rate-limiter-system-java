package com.system.ratelimiter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RateLimitCheckRequest {

    @NotBlank(message = "apiKey is required")
    private String apiKey;

    @NotBlank(message = "route is required")
    private String route;

    @Min(value = 1, message = "tokens must be >= 1")
    private Integer tokens = 1;

    @Pattern(
            regexp = "SLIDING_WINDOW",
            message = "Algorithm must be SLIDING_WINDOW"
    )
    private String algorithm;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public Integer getTokens() {
        return tokens;
    }

    public void setTokens(Integer tokens) {
        this.tokens = tokens;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
