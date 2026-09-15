package org.example.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.util.RedisLockUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper; //负责 Java 对象 ↔ JSON 字符串 之间的相互转换
    private final RedisLockUtil redisLockUtil;

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
                redisTemplate.delete(key); //反序列化读取到脏数据执行垃圾清理
            }
        }

        // 2. 缓存未命中，查数据库
        T result = dbLoader.get(); //把对应SQL的执行权传入到这个方法里，然后用一个Get就代表执行。

        if (result != null) {
            // 3. 数据库存在：存入真实数据（随机 TTL 防雪崩）
            try {
                long ttl = realTtl.getSeconds() + ThreadLocalRandom.current().nextLong(realTtl.getSeconds() / 2);
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), Duration.ofSeconds(ttl));
                log.debug("缓存写入成功，key: {}, ttl: {}s", key, ttl);
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
     * 带有互斥锁的通用缓存旁路模式（防击穿 + 防穿透 + 防雪崩）
     */
    public <T> T getOrLoadWithMutex(String key, Class<T> type, Supplier<T> dbLoader,
                                    Duration realTtl, Duration nullTtl, Duration lockTimeout) {
        // 锁前缀强制写死，防止冲突
        String lockKey = "lock:" + key;
        String lockValue = UUID.randomUUID().toString();

        // 最大重试次数（建议 3 次，重试间隔 50ms，总共最多耗 150ms 等待）
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            // 1. 先查缓存（每次循环都先查，因为可能被其他线程抢先重建了）
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                if ("NULL".equals(json)){
                    log.debug("命中空值标记，key: {}", key);
                    return null;
                }
                try {
                    return objectMapper.readValue(json, type);
                } catch (JsonProcessingException e) {
                    log.error("反序列化失败，删除坏数据 key: {}", key, e);
                    redisTemplate.delete(key);
                    // 删除后继续循环，尝试重建
                }
            }

            // 2. 尝试获取锁。等待时间传 0：重试由下面这层 while 循环负责（3 次 × 50ms），
            //    锁的过期时间才是 lockTimeout。
            boolean locked = redisLockUtil.tryLock(lockKey, lockValue, Duration.ZERO, lockTimeout);
            if (locked) {
                try {
                    // 3. 双重检查（抢到锁后再查一次缓存）,防止在拿到锁之前数据已经有其他人存入缓存
                    json = redisTemplate.opsForValue().get(key);
                    if (json != null) {
                        if ("NULL".equals(json)){
                            log.debug("命中空值标记，key: {}", key);
                            return null;
                        }
                        try {
                            return objectMapper.readValue(json, type);
                        } catch (JsonProcessingException e) {
                            log.error("反序列化缓存失败，key: {}", key, e);
                            // 删除坏数据
                            redisTemplate.delete(key);
                            // 删除后不返回，继续执行下面的查库逻辑
                        }
                    }

                    // 4. 查库并写入缓存（含随机过期）
                    T result = dbLoader.get();

                    if (result != null) {
                        try {
                            long ttl = realTtl.getSeconds() + ThreadLocalRandom.current().nextLong(realTtl.getSeconds() / 2);
                            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), Duration.ofSeconds(ttl));
                            log.debug("缓存写入成功，key: {}, ttl: {}s", key, ttl);
                        } catch (JsonProcessingException e) {
                            log.error("写入缓存失败", e);
                        }
                    } else {
                        try {
                            redisTemplate.opsForValue().set(key, "NULL", nullTtl);
                            log.debug("缓存空值标记，key: {}, ttl: {}s", key, nullTtl.getSeconds());
                        } catch (Exception e) {
                            log.error("缓存空值失败", e);
                        }
                    }
                    return result;
                } finally {
                    // 释放锁
                    Long unlock = redisLockUtil.unlock(lockKey, lockValue);
//                    返回 1 表示删除成功，0 表示删除失败（值不匹配或已过期）
                    if (unlock == 0) {
                        log.error("锁释放失败，可能已过期或被误删，lockKey: {}, lockValue: {}", lockKey, lockValue);
                    }
                }
            } else {
                // 5. 没抢到锁：增加重试计数，短暂等待后继续循环
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // 恢复中断状态，并快速失败退出
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("缓存重建等待被中断", e);
                    }
                }
            }
        }

        // 6. 重试耗尽后的降级策略（根据业务决定）
        log.warn("获取锁超时");
        // 降级方案 A：直接查库（会产生短暂并发查库，但比一直等待要好）
        // 降级方案 B：抛出异常（如果要求极强一致性，就不允许并发查库，直接报错）
        //return dbLoader.get();
        throw new BusinessException("系统繁忙，请稍后重试");
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
        Set<String> keys = redisTemplate.keys(pattern);// 危险！如果 Redis 里有 1000 万个 Key，执行 keys * 会让 Redis 卡死好几分钟（因为要扫描全部数据），导致整个系统超时崩溃。
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("批量删除缓存，模式: {}, 数量: {}", pattern, keys.size());
        }
    }
}