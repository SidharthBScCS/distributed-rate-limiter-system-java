package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.repository.ApiKeyRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    @Test
    void getAll_returnsNormalizedCopiesWithoutWritingBackOnRead() {
        ApiKeyRepository repository = mock(ApiKeyRepository.class);
        ApiKeyService service = new ApiKeyService(repository, "SLIDING_WINDOW", 10);

        ApiKey stored = new ApiKey();
        stored.setId(7L);
        stored.setUserName("user-1");
        stored.setApiKey("key-1");
        stored.setRateLimit(25);
        stored.setWindowSeconds(60);
        stored.setAlgorithm("sliding_window");
        stored.setStatus("Normal");
        stored.setTotalRequests(20L);
        stored.setAllowedRequests(20L);
        stored.setBlockedRequests(0L);

        when(repository.findAll()).thenReturn(List.of(stored));

        List<ApiKey> result = service.getAll();

        assertEquals(1, result.size());
        ApiKey normalized = result.get(0);
        assertNotSame(stored, normalized);
        assertEquals(7L, normalized.getId());
        assertEquals("SLIDING_WINDOW", normalized.getAlgorithm());
        assertEquals(10L, normalized.getAllowedRequests());
        assertEquals(10L, normalized.getBlockedRequests());
        assertEquals("Blocked", normalized.getStatus());

        assertEquals(20L, stored.getAllowedRequests());
        assertEquals(0L, stored.getBlockedRequests());
        assertEquals("Normal", stored.getStatus());
        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyIterable());
    }
}
