package com.enterprise.api.service.impl;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.enterprise.api.config.properties.RateLimitProperties;
import com.enterprise.api.service.IRateLimitService;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Service;

/**
 * In-memory buckets in a Caffeine-backed Bucket4j {@link ProxyManager}. A bucket is
 * dropped once it would be full again, so idle keys do not accumulate. To share
 * limits across instances, replace the proxy manager with a distributed one
 * (Redis, JDBC, Hazelcast); see {@code docs/security.md}.
 */
@Service
public class RateLimitServiceImpl implements IRateLimitService {

    private final boolean enabled;
    private final Map<Limit, BucketConfiguration> configurations = new EnumMap<>(Limit.class);
    private final ProxyManager<String> buckets;

    public RateLimitServiceImpl(RateLimitProperties properties) {
        this.enabled = properties.enabled();
        configurations.put(Limit.LOGIN_PER_IP, configuration(properties.loginPerIp()));
        configurations.put(Limit.LOGIN_PER_USERNAME, configuration(properties.loginPerUsername()));
        configurations.put(Limit.REFRESH_PER_IP, configuration(properties.refreshPerIp()));
        configurations.put(Limit.AUTHENTICATED_PER_USER, configuration(properties.authenticatedPerUser()));
        configurations.put(Limit.ANONYMOUS_PER_IP, configuration(properties.anonymousPerIp()));

        Caffeine<Object, Object> cache = Caffeine.newBuilder().maximumSize(properties.maxBuckets());
        this.buckets = Bucket4jCaffeine.<String>builderFor(cache)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ZERO))
                .build();
    }

    @Override
    public Decision tryConsume(Limit limit, String key) {
        if (!enabled) {
            return Decision.ALLOWED;
        }
        BucketConfiguration configuration = configurations.get(limit);
        ConsumptionProbe probe = buckets.getProxy(limit.name() + ':' + key, () -> configuration)
                .tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.ALLOWED;
        }
        long seconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + TimeUnit.SECONDS.toNanos(1) - 1);
        return new Decision(false, Math.max(1, seconds));
    }

    private static BucketConfiguration configuration(RateLimitProperties.Limit limit) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .build();
    }
}
