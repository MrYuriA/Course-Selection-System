package org.example.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 缓存 Key 生成工具类
 * 将任意对象序列化为 JSON，取 MD5 摘要作为 Key 后缀，确保稳定且长度固定。
 */
public final class CacheKeyUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CacheKeyUtil() {
        // 私有构造器，防止实例化
    }

    /**
     * 生成缓存 Key
     *
     * @param prefix 缓存前缀（如 "course:page:"）
     * @param param  需要作为 Key 依据的参数对象（必须可序列化为 JSON）
     * @return 完整的缓存 Key（如 "course:page:a3f2c1b9d8e7..."）
     * @throws RuntimeException 如果序列化失败（内部捕获 JsonProcessingException 并包装）
     */
    public static String generate(String prefix, Object param) {
        try {
            // 1. 对象 → JSON 字符串
            String json = OBJECT_MAPPER.writeValueAsString(param);
            // 2. JSON → MD5 哈希（固定 32 位十六进制）
            String hash = DigestUtils.md5DigestAsHex(json.getBytes(StandardCharsets.UTF_8));
            // 3. 拼接前缀
            return prefix + hash;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成缓存 Key 失败，前缀: " + prefix + ", 参数: " + param, e);
        }
    }
}