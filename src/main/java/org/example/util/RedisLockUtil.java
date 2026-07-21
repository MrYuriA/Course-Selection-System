package org.example.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final StringRedisTemplate redisTemplate;

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
    public void unlock(String key, String value) {
        String current = redisTemplate.opsForValue().get(key);
        if (value.equals(current)) {
            redisTemplate.delete(key);
        }
    }
}