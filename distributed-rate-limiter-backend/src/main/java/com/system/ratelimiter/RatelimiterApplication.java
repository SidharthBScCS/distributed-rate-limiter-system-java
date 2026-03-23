package com.system.ratelimiter;

import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RatelimiterApplication {
	public static void main(String[] args) {
		normalizeCloudEnvironment();
		SpringApplication.run(RatelimiterApplication.class, args);
	}

	private static void normalizeCloudEnvironment() {
		// Local-first setup: datasource comes directly from application.properties.
		// Keep only Redis URL normalization.
		normalizeRedisConfig();
	}

	private static void normalizeRedisConfig() {
		String redisUrl = firstNonBlank("REDIS_URL", "REDIS_TLS_URL");
		if (isBlank(redisUrl)) {
			return;
		}

		try {
			URI uri = URI.create(redisUrl);
			if (!"redis".equalsIgnoreCase(uri.getScheme()) && !"rediss".equalsIgnoreCase(uri.getScheme())) {
				return;
			}

			setIfMissing("REDIS_HOST", uri.getHost());
			if (uri.getPort() > 0) {
				setIfMissing("REDIS_PORT", String.valueOf(uri.getPort()));
			}
			if ("rediss".equalsIgnoreCase(uri.getScheme())) {
				setIfMissing("REDIS_SSL_ENABLED", "true");
			}

			String userInfo = uri.getUserInfo();
			if (!isBlank(userInfo)) {
				String[] parts = userInfo.split(":", 2);
				if (parts.length == 2) {
					if (!isBlank(parts[0])) {
						setIfMissing("REDIS_USERNAME", parts[0]);
					}
					if (!isBlank(parts[1])) {
						setIfMissing("REDIS_PASSWORD", parts[1]);
					}
				} else if (!isBlank(parts[0])) {
					setIfMissing("REDIS_PASSWORD", parts[0]);
				}
			}
		} catch (IllegalArgumentException ignored) {
			// Keep original env values when REDIS_URL is malformed.
		}
	}

	private static void setIfMissing(String key, String value) {
		if (isBlank(value)) {
			return;
		}
		if (isBlank(firstNonBlank(key))) {
			System.setProperty(key, value);
		}
	}

	private static String firstNonBlank(String... keys) {
		for (String key : keys) {
			String systemValue = System.getProperty(key);
			if (!isBlank(systemValue)) {
				return systemValue;
			}
			String envValue = System.getenv(key);
			if (!isBlank(envValue)) {
				return envValue;
			}
		}
		return null;
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

}
