package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.User;
import com.ecommerce.backend.payload.request.LoginRequest;
import com.ecommerce.backend.payload.request.SignupRequest;
import com.ecommerce.backend.payload.response.JwtResponse;
import com.ecommerce.backend.payload.response.MessageResponse;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.util.JwtUtils;
import com.ecommerce.backend.service.MailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class AuthController {
	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JwtUtils jwtUtils;

	@PostMapping("/login")
	public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
		
		// Match Node.js logic EXACTLY:
		// 1. Find user -> if not exist return 400
		// 2. Check blocked -> return 403
		// 3. Compare password -> return 400
		// 4. Generate tokens
		
		var userOpt = userRepository.findByUsername(loginRequest.getUsername());
		if (userOpt.isEmpty()) {
			return ResponseEntity.badRequest().body(new MessageResponse("Tài khoản không tồn tại"));
		}
		
		User user = userOpt.get();
		if (user.isBlocked()) {
			return ResponseEntity.status(403).body(new MessageResponse("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."));
		}

		// 3. Compare password - Match Node.js: bcrypt.compare(password, this.password)
		if (user.getPassword() == null || !encoder.matches(loginRequest.getPassword(), user.getPassword())) {
			return ResponseEntity.badRequest().body(new MessageResponse("Mật khẩu không chính xác"));
		}
		
		// 4. Generate tokens
		String jwt = jwtUtils.generateJwtToken(user);
		String refreshToken = jwtUtils.generateRefreshToken(user);

		return ResponseEntity.ok(new JwtResponse(jwt,
				refreshToken,
				user.getId(),
				user.getUsername(),
				user.getRole(),
				user.getAvatar()));
	}
	
	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
		if (userRepository.existsByUsername(signUpRequest.getUsername())) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("User already exists"));
		}

		// Create new user's account
		String hashedPassword = encoder.encode(signUpRequest.getPassword());
		System.out.println("DEBUG: Original password: " + signUpRequest.getPassword());
		System.out.println("DEBUG: Hashed password: " + hashedPassword);
		
		User user = User.builder()
				.username(signUpRequest.getUsername())
				.password(hashedPassword)
				.role("user")
				.authType("local")
				.build();

		userRepository.save(user);
		
		System.out.println("DEBUG: Saved user password from DB: " + userRepository.findByUsername(signUpRequest.getUsername()).get().getPassword());

		return ResponseEntity.status(201).body(new MessageResponse("User created successfully"));
	}
	
	@PostMapping("/refresh-token")
	public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
		String refreshToken = request.get("token");
		
		if (refreshToken == null || refreshToken.isEmpty()) {
			return ResponseEntity.badRequest().body(new MessageResponse("Refresh token is required"));
		}
		
		try {
			String username = jwtUtils.getUsernameFromRefreshToken(refreshToken);
			
			// Fetch user for additional info
			var userOpt = userRepository.findByUsername(username);
			if (userOpt.isEmpty()) {
				return ResponseEntity.badRequest().body(new MessageResponse("User not found"));
			}
			
			// Generate new tokens
			String newJwt = jwtUtils.generateJwtToken(userOpt.get());
			
			// Node.js returns: { accessToken: newAccessToken }
			return ResponseEntity.ok(Map.of("accessToken", newJwt));
					
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new MessageResponse("Invalid refresh token"));
		}
	}
	
	@Autowired
	MailService mailService;

	@Value("${google.client.id}")
	String googleClientId;

	@PostMapping("/google-login")
	public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
		String credential = request.get("credential");
		if (credential == null) {
			return ResponseEntity.badRequest().body(new MessageResponse("Google credential missing"));
		}

		try {
			com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier =
					new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
							new com.google.api.client.http.javanet.NetHttpTransport(),
							new com.google.api.client.json.gson.GsonFactory())
							.setAudience(java.util.Collections.singletonList(googleClientId))
							.build();

			com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = verifier.verify(credential);
			
			if (idToken == null) {
				return ResponseEntity.badRequest().body(new MessageResponse("Invalid Google Token"));
			}

			com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = idToken.getPayload();
			String email = payload.getEmail();
			String pictureUrl = (String) payload.get("picture");

			// Check user exists (logic matches Node.js: find by username which stores email)
			User user = userRepository.findByUsername(email).orElse(null);

			if (user != null) {
				if (user.isBlocked()) {
					return ResponseEntity.status(403).body(new MessageResponse("Tài khoản Google này đã bị khóa truy cập hệ thống."));
				}
				// Update avatar
				if ("google".equals(user.getAuthType()) && (user.getAvatar() == null || !pictureUrl.equals(user.getAvatar()))) {
					user.setAvatar(pictureUrl);
					userRepository.save(user);
				}
			} else {
				// Create new user (matching Node.js User constructor)
				user = User.builder()
						.username(email)
						.authType("google")
						.avatar(pictureUrl)
						.role("user") 
						.build();
				userRepository.save(user);
			}

			String jwt = jwtUtils.generateJwtToken(user);
			String refreshToken = jwtUtils.generateRefreshToken(user);

			return ResponseEntity.ok(new JwtResponse(jwt,
					refreshToken,
					user.getId(),
					user.getUsername(),
					user.getRole(),
					user.getAvatar()));

		} catch (Exception e) {
			System.err.println("Google Login Error: " + e.getMessage());
			return ResponseEntity.badRequest().body(new MessageResponse("Google login failed"));
		}
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
		try {
			String email = request.get("email");
			if (email == null || email.isEmpty()) {
				return ResponseEntity.badRequest().body(new MessageResponse("Vui lòng nhập email"));
			}

			User user = userRepository.findByUsername(email).orElse(null);
			
			if (user == null) {
				return ResponseEntity.status(404).body(new MessageResponse("Email not found"));
			}
			
			if (user.isBlocked()) {
				return ResponseEntity.status(403).body(new MessageResponse("Account is blocked"));
			}
			
			if ("google".equals(user.getAuthType())) {
				return ResponseEntity.badRequest().body(new MessageResponse("Google account cannot reset password here"));
			}

			// Generate token
			String token = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32); 
			user.setResetPasswordToken(token);
			user.setResetPasswordExpires(new java.util.Date(System.currentTimeMillis() + 3600000)); // 1 hour
			userRepository.save(user);

			String resetUrl = "http://localhost:5173/reset-password/" + token;

			mailService.sendEmail(user.getUsername(), "Password Reset Request", "Click the link to reset your password: " + resetUrl);
			return ResponseEntity.ok(new MessageResponse("Reset link sent to your email"));
			
		} catch (Exception e) {
			e.printStackTrace(); // Print to console for debugging
			return ResponseEntity.internalServerError().body(new MessageResponse("Internal Error: " + e.toString()));
		}
	}
	
	@PostMapping("/reset-password")
	public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
		String token = request.get("token");
		String newPassword = request.get("newPassword");
		
		if (token == null || newPassword == null) {
			return ResponseEntity.badRequest().body(new MessageResponse("Token and newPassword are required"));
		}
		
		java.util.Date now = new java.util.Date();
		User user = userRepository.findByResetPasswordTokenAndResetPasswordExpiresGreaterThan(token, now).orElse(null);
		
		if (user == null) {
			return ResponseEntity.badRequest().body(new MessageResponse("Token is invalid or has expired"));
		}
		
		user.setPassword(encoder.encode(newPassword));
		user.setResetPasswordToken(null);
		user.setResetPasswordExpires(null);
		userRepository.save(user);
		
		return ResponseEntity.ok(new MessageResponse("Password has been reset successfully"));
	}
}
