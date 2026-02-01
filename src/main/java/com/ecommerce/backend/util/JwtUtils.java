package com.ecommerce.backend.util;

import com.ecommerce.backend.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {
	private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Value("${app.jwt.expiration-ms}")
	private int jwtExpirationMs;

	@Value("${app.jwt.refresh-expiration-ms}")
	private int refreshJwtExpirationMs;

	public String generateJwtToken(User user) {
		return Jwts.builder()
				.subject(user.getUsername())
				.claim("id", user.getId())
				.claim("username", user.getUsername())
				.claim("role", user.getRole())
				.issuedAt(new Date())
				.expiration(new Date((new Date()).getTime() + jwtExpirationMs))
				.signWith(key(), Jwts.SIG.HS256)
				.compact();
	}

	public String generateRefreshToken(User user) {
		return Jwts.builder()
				.subject(user.getUsername())
				.claim("id", user.getId())
				.claim("username", user.getUsername())
				.claim("role", user.getRole())
				.issuedAt(new Date())
				.expiration(new Date((new Date()).getTime() + refreshJwtExpirationMs))
				.signWith(key(), Jwts.SIG.HS256)
				.compact();
	}

	public String getUserNameFromJwtToken(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(key())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		
		String username = claims.getSubject(); // 'sub'
		if (username == null) {
			username = claims.get("username", String.class);
		}
		return username;
	}

	public String getUsernameFromRefreshToken(String token) {
		return getUserNameFromJwtToken(token);
	}

	private SecretKey key() {
		byte[] keyBytes = jwtSecret.getBytes();
		// HS256 requires at least 256 bits (32 bytes). 
		// If the secret is shorter (like "dunglv"), we must pad it or hash it to reach 32 bytes.
		// To match Node.js behavior which is more lenient, we can pad with zeros.
		if (keyBytes.length < 32) {
			byte[] paddedKey = new byte[32];
			System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
			return Keys.hmacShaKeyFor(paddedKey);
		}
		return Keys.hmacShaKeyFor(keyBytes);
	}

	public boolean validateJwtToken(String authToken) {
		try {
			Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
			return true;
		} catch (MalformedJwtException e) {
			logger.error("Invalid JWT token: {}", e.getMessage());
		} catch (ExpiredJwtException e) {
			logger.error("JWT token is expired: {}", e.getMessage());
		} catch (UnsupportedJwtException e) {
			logger.error("JWT token is unsupported: {}", e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.error("JWT claims string is empty: {}", e.getMessage());
		} catch (Exception e) {
             // Catch weak key exception if it occurs during init, but mainly validation here
            logger.error("JWT validation error: {}", e.getMessage());
        }

		return false;
	}
}
