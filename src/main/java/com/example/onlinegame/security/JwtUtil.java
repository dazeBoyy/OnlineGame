package com.example.onlinegame.security;


import com.example.onlinegame.model.user.User;
import com.example.onlinegame.service.user.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@CommonsLog
@Component
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private final CustomUserDetailsService userDetailsService;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserPrincipal user, List<String> roles) {
        return Jwts.builder()
                .setSubject(user.getUserId().toString()) // Используем ID как subject
                .claim("username", user.getUsername()) // Добавляем username как claim
                .claim("roles", roles) // Добавляем роли в токен
                .claim("user_id", user.getUserId()) // Явно добавляем ID
                .setIssuedAt(new Date()) // Устанавливаем время создания токена
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSecretKey())
                .compact();
    }
    public String generateToken(User user, List<String> roles) {
        return Jwts.builder()
                .setSubject(user.getId().toString()) // Используем ID как subject
                .claim("username", user.getUsername()) // Добавляем username как claim
                .claim("roles", roles) // Добавляем роли в токен
                .claim("user_id", user.getId()) // Явно добавляем ID
                .setIssuedAt(new Date()) // Устанавливаем время создания токена
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSecretKey())
                .compact();
    }
    public boolean validateToken(String token) {
        try {

            Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Error validating JWT token: " + e.getMessage(), e);
            return false;
        }
    }


    public UserPrincipal getUserPrincipal(String token) {
        String username = getUsernameFromToken(token);
        return (UserPrincipal) userDetailsService.loadUserByUsername(username);
    }


    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("username", String.class);
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.parseLong(claims.getSubject());
    }
}