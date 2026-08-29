package collabdesk.infrastructure.cache;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@Profile("redis")
public class CacheConfiguration implements CachingConfigurer {

    public static final String WORKSPACE_PROJECT_ACCESS_OVERVIEW =
            "workspaceProjectAccessOverview";

    private static final String KEY_PREFIX = "collabdesk:v1:";
    private static final Duration OVERVIEW_TTL = Duration.ofMinutes(5);

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        var keySerializer = RedisSerializationContext
                .SerializationPair
                .fromSerializer(new StringRedisSerializer());

        var valueSerializer = RedisSerializationContext
                .SerializationPair
                .fromSerializer(new JacksonJsonRedisSerializer<>(
                        WorkspaceProjectAccessOverviewResponse.class
                ));

        var overviewConfiguration =
                RedisCacheConfiguration
                        .defaultCacheConfig()
                        .entryTtl(OVERVIEW_TTL)
                        .disableCachingNullValues()
                        .computePrefixWith(
                                cacheName -> KEY_PREFIX + cacheName + "::"
                        )
                        .serializeKeysWith(keySerializer)
                        .serializeValuesWith(valueSerializer);

        RedisCacheWriter cacheWriter = RedisCacheWriter
                .nonLockingRedisCacheWriter(
                        connectionFactory,
                        BatchStrategies.scan(1000)
                );

        return RedisCacheManager.builder(cacheWriter)
                .withCacheConfiguration(
                        WORKSPACE_PROJECT_ACCESS_OVERVIEW,
                        overviewConfiguration
                )
                .disableCreateOnMissingCache()
                .build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LenientRedisCacheErrorHandler();
    }
}
