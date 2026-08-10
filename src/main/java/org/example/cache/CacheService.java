package org.example.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 通用缓存旁路模式（防穿透 + 防雪崩）
     *
     * @param key      缓存 Key
     * @param type     返回对象类型（用于反序列化）
     * @param dbLoader 数据库查询逻辑（Lambda）
     * @param realTtl  真实数据过期时间
     * @param nullTtl  空值标记过期时间
     * @param <T>      泛型
     * @return 实体对象或 null
     */
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> dbLoader,
                           Duration realTtl, Duration nullTtl) {
        // 1. 先查 Redis
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            // 命中空值标记（防穿透）
            if ("NULL".equals(json)) {
                log.debug("命中空值标记，key: {}", key);
                return null;
            }
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                log.error("反序列化失败，删除坏数据 key: {}", key, e);
                redisTemplate.delete(key);
            }
        }

        // 2. 缓存未命中，查数据库
        T result = dbLoader.get();

        if (result != null) {
            // 3. 数据库存在：存入真实数据（随机 TTL 防雪崩）
            try {
                long baseSeconds = realTtl.getSeconds();
                long randomSeconds = ThreadLocalRandom.current().nextLong(baseSeconds / 2);
                long actualTtl = baseSeconds + randomSeconds;
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result),
                        Duration.ofSeconds(actualTtl));
                log.debug("缓存写入成功，key: {}, ttl: {}s", key, actualTtl);
            } catch (Exception e) {
                log.error("写入缓存失败", e);
            }
            return result;
        } else {
            // 4. 数据库不存在：缓存空值标记（防穿透）
            try {
                redisTemplate.opsForValue().set(key, "NULL", nullTtl);
                log.debug("缓存空值标记，key: {}, ttl: {}s", key, nullTtl.getSeconds());
            } catch (Exception e) {
                log.error("缓存空值失败", e);
            }
            return null;
        }
    }

    /**
     * 删除单个缓存
     */
    public void evict(String key) {
        redisTemplate.delete(key);
        log.debug("删除缓存: {}", key);
    }

    /**
     * 按模式批量删除缓存（例如：course:list:*）
     */
    public void evictPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("批量删除缓存，模式: {}, 数量: {}", pattern, keys.size());
        }
    }
}