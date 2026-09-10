package collabdesk.workspace.access.service;

import collabdesk.RedisTestcontainersConfiguration;
import collabdesk.TestcontainersConfiguration;
import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("redis")
@Import({
        TestcontainersConfiguration.class,
        RedisTestcontainersConfiguration.class
})
@Execution(ExecutionMode.SAME_THREAD)
class WorkspaceProjectAccessRedisIntegrationTest {

    private static final String KEY_PREFIX =
            "collabdesk:v1:workspaceProjectAccessOverview::";

    @Autowired
    private WorkspaceProjectAccessOverviewCacheService cacheService;

    @Autowired
    private WorkspaceProjectAccessChangePublisher accessChangePublisher;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redisContainer;

    @MockitoBean
    private WorkspaceProjectAccessOverviewQueryService queryService;

    @Test
    void coversCacheHitUserKeysTtlCommitAndRollback() throws Exception {
        WorkspaceProjectAccessOverviewResponse response = emptyOverview();
        when(queryService.findForWorkspace(42L, 7L)).thenReturn(response);
        when(queryService.findForWorkspace(42L, 9L)).thenReturn(response);
        when(queryService.findForWorkspace(51L, 9L)).thenReturn(response);

        String firstKey = KEY_PREFIX + "42:7";
        String secondKey = KEY_PREFIX + "42:9";
        String otherWorkspaceKey = KEY_PREFIX + "51:9";
        cacheService.findForWorkspace(42L, 7L);
        await(() -> redisTemplate.hasKey(firstKey), Duration.ofSeconds(2));
        cacheService.findForWorkspace(42L, 7L);
        cacheService.findForWorkspace(42L, 9L);
        cacheService.findForWorkspace(51L, 9L);

        assertTrue(redisTemplate.hasKey(firstKey));
        assertTrue(redisTemplate.hasKey(secondKey));
        assertTrue(redisTemplate.hasKey(otherWorkspaceKey));

        String json = redisTemplate.opsForValue().get(firstKey);
        assertNotNull(json);
        assertTrue(json.contains("\"projects\""));

        Long ttl = redisTemplate.getExpire(firstKey);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 300);

        verify(queryService, times(1)).findForWorkspace(42L, 7L);
        verify(queryService, times(1)).findForWorkspace(42L, 9L);

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> {
                    accessChangePublisher.publish(42L);
                    status.setRollbackOnly();
                }
        );

        assertStaysPresent(firstKey, Duration.ofMillis(250));

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accessChangePublisher.publish(42L)
        );

        await(
                () -> !redisTemplate.hasKey(firstKey)
                        && !redisTemplate.hasKey(secondKey)
                        && !redisTemplate.hasKey(otherWorkspaceKey),
                Duration.ofSeconds(2)
        );
        assertFalse(redisTemplate.hasKey(firstKey));
        assertFalse(redisTemplate.hasKey(secondKey));
        assertFalse(redisTemplate.hasKey(otherWorkspaceKey));

        WorkspaceProjectAccessOverviewResponse outageResponse = emptyOverview();
        when(queryService.findForWorkspace(99L, 7L))
                .thenReturn(outageResponse);
        redisContainer.stop();

        WorkspaceProjectAccessOverviewResponse fallback =
                cacheService.findForWorkspace(99L, 7L);

        assertSame(outageResponse, fallback);
        verify(queryService).findForWorkspace(99L, 7L);
    }

    private WorkspaceProjectAccessOverviewResponse emptyOverview() {
        return new WorkspaceProjectAccessOverviewResponse(List.of());
    }

    private void assertStaysPresent(String key, Duration duration)
            throws InterruptedException {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            assertTrue(redisTemplate.hasKey(key));
            Thread.sleep(20);
        }
    }

    private void await(BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Redis cache condition was not met within " + timeout);
    }
}
