package org.example.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final StringRedisTemplate redisTemplate;

    // 默认锁过期时间。必须显著大于一次临界区耗时，否则锁会在业务写完之前自动失效，其他线程就能同时进临界区。
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(10);

    // 重试间隔：首次 10ms，之后指数退避，上限 50ms，避免抢锁线程把 Redis 打满。
    private static final long RETRY_INTERVAL_INIT_MS = 10;
    private static final long RETRY_INTERVAL_MAX_MS = 50;

    // 1. 定义为静态常量，只加载一次，避免重复编译
    private static final RedisScript<Long> UNLOCK_SCRIPT;

    static {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "   return redis.call('del', KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";
        UNLOCK_SCRIPT = new DefaultRedisScript<>(script, Long.class);
    }

    /**
     * 尝试获取锁，获取不到时自旋重试，直到 waitTime 用尽。
     * @param key 锁的key（如 "lock:course:1"）
     * @param value 唯一标识（可用学生ID或UUID）
     * @param waitTime 最长等待时间（不是锁的过期时间）
     * @return 是否获取成功
     */
    public boolean tryLock(String key, String value, Duration waitTime) {
        return tryLock(key, value, waitTime, DEFAULT_TTL);
    }

    /**
     * @param waitTime 最长等待时间；到点仍未抢到就返回 false
     * @param ttl 锁的过期时间，防止持有者宕机导致死锁
     */
    public boolean tryLock(String key, String value, Duration waitTime, Duration ttl) {
        long deadline = System.nanoTime() + waitTime.toNanos();
        long intervalMs = RETRY_INTERVAL_INIT_MS;

        while (true) {
            //setIfAbsent（即 Redis 的 SETNX 命令）是原子操作
            // 当且仅当 Key 不存在时，才会设置值并返回 true。
            // 保证了在并发环境下，只有一个线程能成功设置这把锁。
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl))) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            intervalMs = Math.min(intervalMs * 2, RETRY_INTERVAL_MAX_MS);
        }
    }

    /**
     * 释放锁（需校验value是否匹配，避免误删别人的锁）
     */
    public Long unlock(String key, String value) {
        // 2. 执行 Lua 脚本，返回 1 表示删除成功，0 表示删除失败（值不匹配或已过期）
        Long result = redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key), // KEYS[1]
                value                           // ARGV[1]
        );
      return  result;
    }
}