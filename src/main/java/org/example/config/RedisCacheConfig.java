package org.example.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {

        //配置翻译规则（ObjectMapper）→ 交给执行者序列化器（Generic...Serializer）→ 存入 Config（.serializeValuesWith）
        //本配置类主要针对Value的序列化规则进行配置然后交给序列化器，最后存入Config 返回一个基于自定义好的Config的RedisCacheManager

        //  1. 造一本“翻译手册”（ObjectMapper），并注册时间支持（JavaTimeModule）
        //Jackson 库的核心类。所有 Java 对象和 JSON 之间的互转，都由它负责。
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());  // <--- 这就是那本手册！
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 可选：把时间写成 2026-07-27 这种格式，而不是一串数字，具体就是关闭默认写入策略转而执行唯一可选项ISO-8601

        // 2.  关键：启用类型信息，让 JSON 里包含 @class 字段
        objectMapper.activateDefaultTyping( //开启“多态类型处理”。意思是：序列化时，在 JSON 中额外塞一个 @class 字段，告诉取出者“我原本是哪个类的对象”。
                objectMapper.getPolymorphicTypeValidator(),//固定的按照白名单规则生效的拦截器
                ObjectMapper.DefaultTyping.NON_FINAL,//指定哪些类型才有Class字段
                JsonTypeInfo.As.PROPERTY //指定存在哪里{"@class":"org.example.pojo.Course", "id":1, "name":"Java"}
        );

        //  3. 把手册交给师傅（序列化器）
        //Spring Data Redis 提供的序列化器实现
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 4. 配置车间规则（过期时间、钥匙格式等）
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))//所有缓存10分钟后自动过期删除
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))//缓存Key存储时的序列化方式,存储为普通字符
                //↑ 为了保证设置能够适应大量不同的序列化器所以serializeKeysWith的参数要求类型为接口，为了符合参数要求就生成了一个序列化器对象new StringRedisSerializer()然后用RedisSerializationContext.SerializationPair.fromSerializer把序列化器对象包装成了接口
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)) //缓存Value存储时的序列化方式用带手册的师傅，并且上面书写的规则正在此时存入config
                .disableCachingNullValues();//如果业务代码查询结果为 null，不存入 Redis。避免缓存大量无意义的空数据占用内存。

        return RedisCacheManager.builder(factory)//创建执行者 RedisCacheWriter writer = new DefaultRedisCacheWriter(factory); // 操作工已就位！
                .cacheDefaults(config) // builder.defaultConfiguration = config; // 把规则书放进了 builder 的抽屉里。
                .build(); //组装在一起 return new RedisCacheManager(writer, defaultConfiguration);

        //builder 的设计模式（Builder Pattern）非常讲究 “延迟组装”。
        // 它允许先给工头（builder）塞操作工、塞规则书，甚至塞十几种不同的零件
        // 最后一声令下（.build()），它才把一堆零散的零件瞬间拼成一台完整的机器。
    }
}