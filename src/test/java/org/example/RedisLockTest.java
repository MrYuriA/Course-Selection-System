package org.example;

import org.example.util.RedisLockUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class RedisLockTest {

    @Autowired
    private RedisLockUtil lockService; // 你的新服务
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testUnlockSafety() {
        String key = "test:lock";
        String valueA = "UUID-A";
        String valueB = "UUID-B";

        // ========== 第一步：线程 A 加锁成功 ==========
        System.out.println("1. 线程 A 加锁，存入 valueA");
        redisTemplate.opsForValue().set(key, valueA);

        // ========== 第二步：模拟锁过期 ==========
        // 真实场景是 Redis 自动过期，测试时为了不等那几十秒，我们手动模拟“锁失效”
        System.out.println("2. 模拟锁过期（手动删除 A 的锁）");
        redisTemplate.delete(key);

        // 此时，A 的业务逻辑可能刚刚执行完，A 的本地变量里还记录着 valueA 的旧值。
        // 而 Redis 里这把锁已经是空的了。

        // ========== 第三步：线程 B 抢到了这把锁 ==========
        System.out.println("3. 线程 B 加锁，存入 valueB");
        redisTemplate.opsForValue().set(key, valueB);

        // 此时 Redis 里存的是 B，但 A 的程序刚执行到 unlock 方法，手头拿着旧的 valueA

        // ========== 第四步：A 用旧签名去释放锁（致命操作） ==========
        System.out.println("4. 线程 A 拿着旧签名 'UUID-A' 调用 Lua 脚本解锁");
        Long result = lockService.unlock(key, valueA); // 假设 unlock 返回 Long

        // ========== 验证结果 ==========
        String currentValue = redisTemplate.opsForValue().get(key);
        System.out.println("5. 当前 Redis 中的锁值: " + currentValue);
        System.out.println("6. Lua 脚本返回结果: " + result);

        // 断言：返回 0（删除失败），且 Redis 里依然是 B（没被误删）
        assert result == 0;
        assert valueB.equals(currentValue);

        System.out.println("✅ 验证通过！A 没能删掉 B 的锁。");
    }
}