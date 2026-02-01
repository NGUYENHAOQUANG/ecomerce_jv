package com.ecommerce.backend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtResponse {
	private String token;
	private String refreshToken;
	private String id;
	private String username;
	private String role;
	private String avatar;
}
