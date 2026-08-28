package org.example.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.example.dto.TokenInfo;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 密钥，至少 256 位，生产环境放配置中心
    private static final String SECRET = "YourSuperSecretKeyForJWT1234567890abcdefghijklmnop";
    private static final long EXPIRATION = 1000 * 60 * 60 * 24; // 24小时

    //用指定的算法把转换成字符串的SECRET作为密钥，SecretKey代表一组固定好的转换规则和密钥
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    // 生成 Token
    public String generateToken(Long studentId ,Integer role) {
        return Jwts.builder()
                .claim("role", role)
                .subject(studentId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }

    // 从 Token 中解析出学生 ID和角色
    public TokenInfo parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenInfo(
                Long.parseLong(claims.getSubject()),
                claims.get("role", Integer.class)
        );
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}