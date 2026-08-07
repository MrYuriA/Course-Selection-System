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
     * 尝试获取锁
     * @param key 锁的key（如 "lock:course:1"）
     * @param value 唯一标识（可用学生ID或UUID）
     * @param timeout 锁过期时间
     * @return 是否获取成功
     */
    public boolean tryLock(String key, String value, Duration timeout) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                        .setIfAbsent(key, value, timeout)
        );
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