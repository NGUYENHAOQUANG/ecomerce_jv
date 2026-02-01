package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.User;
import com.ecommerce.backend.payload.request.LoginRequest;
import com.ecommerce.backend.payload.request.SignupRequest;
import com.ecommerce.backend.payload.response.JwtResponse;
import com.ecommerce.backend.payload.response.MessageResponse;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.security.service.UserDetailsImpl;
import com.ecommerce.backend.util.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JwtUtils jwtUtils;

	@PostMapping("/login")
	public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
		
		// 0. Check if user exists (Optional, AuthenticationManager handles it but for custom error message)
		// But in Node.js:
		// 1. Find user -> if not exist return 400
		// 2. Check blocked -> return 403
		// 3. Compare password -> return 400
		
		// We can use AuthenticationManager, but catching exceptions to match Node.js responses exactly
		// might be tricky. Let's try standard Spring Security flow first.
		
		// Actually, to match Node.js logic of "User blocked" BEFORE password check (or after),
		// we should fetch user first.
		
		var userOpt = userRepository.findByUsername(loginRequest.getUsername());
		if (userOpt.isEmpty()) {
			return ResponseEntity.badRequest().body(new MessageResponse("Tài khoản không tồn tại"));
		}
		
		User user = userOpt.get();
		if (user.isBlocked()) {
			return ResponseEntity.status(403).body(new MessageResponse("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."));
		}

		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

			SecurityContextHolder.getContext().setAuthentication(authentication);
			
			String jwt = jwtUtils.generateJwtToken(loginRequest.getUsername());
			String refreshToken = jwtUtils.generateRefreshToken(loginRequest.getUsername());

			UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal(); // Assuming local auth uses this
			
			// If we are here, password matched.
			
			return ResponseEntity.ok(new JwtResponse(jwt,
					refreshToken,
					userDetails.getId(),
					userDetails.getUsername(),
					userDetails.getRole(),
					user.getAvatar())); // Fetch avatar from entity, userDetails might not have it updated? 
										// Actually UserDetailsImpl has fields from User entity at build time.
										// But avatar is not in UserDetailsImpl constructor I made? 
										// I made it in UserDetailsImpl. Let's check constructor.
										// UserDetailsImpl definition has avatar? I didn't add it in the file tool call I made earlier?
										// I need to check UserDetailsImpl. It had `id, username, email, password, role, isBlocked`.
										// I forgot `avatar` in UserDetailsImpl!
										// I should probably fetch it from `user` object directly here since I have it.
										// Yes `user` variable is available. Use `user.getAvatar()`.

		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new MessageResponse("Mật khẩu không chính xác"));
		}
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
		if (userRepository.existsByUsername(signUpRequest.getUsername())) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("User already exists"));
		}

		// Create new user's account
		User user = User.builder()
				.username(signUpRequest.getUsername())
				.password(encoder.encode(signUpRequest.getPassword()))
				.role("user")
				.authType("local")
				.build();

		userRepository.save(user);

		return ResponseEntity.status(201).body(new MessageResponse("User created successfully"));
	}
	
	@PostMapping("/refresh-token")
	public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
		String requestRefreshToken = request.get("token");
		
		if (requestRefreshToken == null || requestRefreshToken.isEmpty()) {
			return ResponseEntity.status(403).body(new MessageResponse("No refresh token provided"));
		}
		
		if (jwtUtils.validateJwtToken(requestRefreshToken)) {
			String username = jwtUtils.getUserNameFromJwtToken(requestRefreshToken);
			
			// Check if user is blocked or deleted
			var userOpt = userRepository.findByUsername(username);
			if (userOpt.isEmpty() || userOpt.get().isBlocked()) {
				return ResponseEntity.status(403).body(new MessageResponse("User blocked or not found"));
			}
			
			String token = jwtUtils.generateJwtToken(username);
			// Return as JSON with accessToken key
			// Node.js returns: { accessToken: newAccessToken }
			return ResponseEntity.ok(Map.of("accessToken", token));
		} else {
			return ResponseEntity.status(403).body(new MessageResponse("Invalid or expired refresh token"));
		}
	}
	@Autowired
	com.ecommerce.backend.service.MailService mailService;

	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
		String email = request.get("email");
		if (email == null || email.isEmpty()) {
			return ResponseEntity.badRequest().body(new MessageResponse("Vui lòng nhập email"));
		}

		User user = userRepository.findByEmail(email).orElse(null);
		
		if (user == null) {
			return ResponseEntity.badRequest().body(new MessageResponse("Email không tồn tại trong hệ thống"));
		}
		
		// Check block
		if (user.isBlocked()) {
			return ResponseEntity.status(403).body(new MessageResponse("Account is blocked"));
		}
		
		if ("google".equals(user.getAuthType())) {
			return ResponseEntity.badRequest().body(new MessageResponse("Google account cannot reset password here"));
		}

		// Generate token
		String token = java.util.UUID.randomUUID().toString();
		user.setResetPasswordToken(token);
		user.setResetPasswordExpires(new java.util.Date(System.currentTimeMillis() + 3600000)); // 1 hour
		userRepository.save(user);

		String resetUrl = "http://localhost:5173/reset-password/" + token;
		String emailContent = "Click the link to reset your password: " + resetUrl;

		try {
			mailService.sendEmail(email, "Password Reset Request", emailContent);
			return ResponseEntity.ok(new MessageResponse("Reset link sent to your email"));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(new MessageResponse("Lỗi khi gửi email: " + e.getMessage()));
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

			// Check user exists
			User user = userRepository.findByEmail(email).orElse(null);
			if (user == null) {
				// Also check by username if logic uses email as username
				user = userRepository.findByUsername(email).orElse(null);
			}

			if (user != null) {
				if (user.isBlocked()) {
					return ResponseEntity.status(403).body(new MessageResponse("Tài khoản Google này đã bị khóa truy cập hệ thống."));
				}
				// Update avatar
				if ("google".equals(user.getAuthType()) && pictureUrl != null && !pictureUrl.equals(user.getAvatar())) {
					user.setAvatar(pictureUrl);
					userRepository.save(user);
				}
			} else {
				// Create new user
				user = User.builder()
						.username(email)
						.email(email)
						.authType("google")
						.avatar(pictureUrl)
						.role("user") 
						.build();
				userRepository.save(user);
			}

			String jwt = jwtUtils.generateJwtToken(user.getUsername());
			String refreshToken = jwtUtils.generateRefreshToken(user.getUsername());

			return ResponseEntity.ok(new JwtResponse(jwt,
					refreshToken,
					user.getId(),
					user.getUsername(),
					user.getRole(),
					user.getAvatar()));

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().body(new MessageResponse("Google login failed"));
		}
	}
}
