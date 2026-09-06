package collabdesk.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Makes cache failures non-fatal: reads fall back to the database and failed
 * writes or evictions are logged without breaking business operations.
 */
public final class LenientRedisCacheErrorHandler
        implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(
            LenientRedisCacheErrorHandler.class
    );

    @Override
    public void handleCacheGetError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        warn("get", cache, key, exception);
    }

    @Override
    public void handleCachePutError(
            RuntimeException exception,
            Cache cache,
            Object key,
            Object value
    ) {
        warn("put", cache, key, exception);
    }

    @Override
    public void handleCacheEvictError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        warn("evict", cache, key, exception);
    }

    @Override
    public void handleCacheClearError(
            RuntimeException exception,
            Cache cache
    ) {
        warn("clear", cache, null, exception);
    }

    private void warn(
            String operation,
            Cache cache,
            Object key,
            RuntimeException exception
    ) {
        log.warn(
                "Redis cache operation failed: operation={}, cache={}, "
                        + "key={}, exception={}",
                operation,
                cache.getName(),
                key,
                exception.getClass().getSimpleName()
        );
        log.debug("Redis cache failure details", exception);
    }
}
