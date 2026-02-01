package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Cart;
import com.ecommerce.backend.model.User;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.security.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/user")
public class UserController {

	@Autowired
	UserRepository userRepository;

	@Autowired
	CartRepository cartRepository;

	@GetMapping("/info/{userId}")
	public ResponseEntity<?> getInfoUser(@PathVariable("userId") String userId) {
		User user = userRepository.findById(userId)
				.orElse(null);

		if (user == null) {
			return ResponseEntity.status(404).body(Map.of("message", "User not found"));
		}

		// Calculate total cart items
		List<Cart> carts = cartRepository.findByUserId(userId);
		int amountCart = carts.stream().mapToInt(Cart::getQuantity).sum();

		// Construct response data
		Map<String, Object> data = new HashMap<>();
		data.put("id", user.getId());
		data.put("username", user.getUsername());
		data.put("email", (user.getEmail() != null && !user.getEmail().isEmpty()) ? user.getEmail() : user.getUsername());
		data.put("role", user.getRole());
		data.put("avatar", user.getAvatar());
		data.put("authType", user.getAuthType());
		data.put("amountCart", amountCart);

		// Profile fields
		data.put("firstName", user.getFirstName());
		data.put("lastName", user.getLastName());
		data.put("phone", user.getPhone());
		data.put("street", user.getStreet());
		data.put("apartment", user.getApartment());
		data.put("cities", user.getCities());
		data.put("state", user.getState());
		data.put("zipCode", user.getZipCode());
		data.put("country", user.getCountry());

		// Timestamps
		data.put("createdAt", user.getCreatedAt());
		data.put("updatedAt", user.getUpdatedAt());
		data.put("deletedAt", user.getDeletedAt());

		return ResponseEntity.ok(Map.of(
				"msg", "Get info user successfully",
				"data", data
		));
	}

	@PutMapping("/profile")
	public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetailsImpl userDetails,
			@RequestBody Map<String, Object> updates) {
		
		String userId = userDetails.getId();
		User user = userRepository.findById(userId).orElse(null);
		
		if (user == null) {
			return ResponseEntity.status(404).body(Map.of("message", "User not found"));
		}

		// Allowed updates
		if (updates.containsKey("firstName")) user.setFirstName((String) updates.get("firstName"));
		if (updates.containsKey("lastName")) user.setLastName((String) updates.get("lastName"));
		if (updates.containsKey("phone")) user.setPhone((String) updates.get("phone"));
		if (updates.containsKey("email")) user.setEmail((String) updates.get("email"));
		if (updates.containsKey("street")) user.setStreet((String) updates.get("street"));
		if (updates.containsKey("apartment")) user.setApartment((String) updates.get("apartment"));
		if (updates.containsKey("cities")) user.setCities((String) updates.get("cities"));
		if (updates.containsKey("state")) user.setState((String) updates.get("state"));
		if (updates.containsKey("country")) user.setCountry((String) updates.get("country"));
		if (updates.containsKey("zipCode")) user.setZipCode((String) updates.get("zipCode"));
		if (updates.containsKey("avatar")) user.setAvatar((String) updates.get("avatar"));

		User updatedUser = userRepository.save(user);

		// Remove password from response
		updatedUser.setPassword(null);

		return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "Profile updated successfully",
				"data", updatedUser
		));
	}
}
