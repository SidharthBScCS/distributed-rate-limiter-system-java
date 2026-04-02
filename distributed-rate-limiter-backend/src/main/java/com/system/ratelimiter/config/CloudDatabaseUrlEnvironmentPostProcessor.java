package com.system.ratelimiter.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class CloudDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "cloudDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicitDatasourceUrl = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("DB_URL")
        );
        if (explicitDatasourceUrl != null) {
            return;
        }

        String databaseUrl = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("POSTGRES_URL")
        );
        if (databaseUrl == null) {
            return;
        }

        ParsedDatabaseUrl parsed = parseDatabaseUrl(databaseUrl);
        if (parsed == null) {
            return;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", parsed.jdbcUrl());

        String explicitUsername = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_USERNAME"),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("DB_USERNAME")
        );
        if (explicitUsername == null && parsed.username() != null && !parsed.username().isBlank()) {
            properties.put("spring.datasource.username", parsed.username());
        }

        String explicitPassword = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("DB_PASSWORD")
        );
        if (explicitPassword == null && parsed.password() != null) {
            properties.put("spring.datasource.password", parsed.password());
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static ParsedDatabaseUrl parseDatabaseUrl(String databaseUrl) {
        try {
            URI uri = URI.create(databaseUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }

            String normalizedScheme = scheme.toLowerCase();
            if (!"postgres".equals(normalizedScheme) && !"postgresql".equals(normalizedScheme)) {
                return null;
            }

            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            if (host == null || path == null || path.isBlank() || "/".equals(path)) {
                return null;
            }

            String database = path.startsWith("/") ? path.substring(1) : path;
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            String query = uri.getRawQuery();
            if (query != null && !query.isBlank()) {
                jdbcUrl += "?" + query;
            }

            String username = null;
            String password = null;
            String userInfo = uri.getRawUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                int separatorIndex = userInfo.indexOf(':');
                if (separatorIndex >= 0) {
                    username = decode(userInfo.substring(0, separatorIndex));
                    password = decode(userInfo.substring(separatorIndex + 1));
                } else {
                    username = decode(userInfo);
                }
            }

            return new ParsedDatabaseUrl(jdbcUrl, username, password);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ParsedDatabaseUrl(String jdbcUrl, String username, String password) {
    }
}
