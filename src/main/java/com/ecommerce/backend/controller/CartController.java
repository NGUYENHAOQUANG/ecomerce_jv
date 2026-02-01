package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Cart;
import com.ecommerce.backend.model.Product;
import com.ecommerce.backend.payload.response.CartItemResponse;

import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class CartController {

	@Autowired
	CartRepository cartRepository;

	@Autowired
	ProductRepository productRepository;

	@GetMapping("/cart/{userId}")
	public ResponseEntity<?> getCart(@PathVariable String userId) {
		try {
			List<Cart> carts = cartRepository.findByUserId(userId);
			
			// Get product details
			List<String> productIds = carts.stream().map(Cart::getProductId).collect(Collectors.toList());
			List<Product> products = productRepository.findAllById(productIds);
			
			Map<String, Product> productMap = products.stream()
					.collect(Collectors.toMap(Product::getId, p -> p, (p1, p2) -> p1)); // Handle duplicate IDs if any

			List<CartItemResponse> response = carts.stream().map(item -> {
				Product p = productMap.get(item.getProductId());
				if (p == null) return null; // Logic implies product exists
				
				return CartItemResponse.builder()
						.name(p.getName())
						.price(p.getPrice())
						.quantity(item.getQuantity())
						.size(item.getSize())
						.sku("87654") // Hardcoded in Node.js
						.total(item.getQuantity() * p.getPrice())
						.images(p.getImages())
						.productId(item.getProductId())
						.userId(item.getUserId())
						.build();
			}).filter(Objects::nonNull).collect(Collectors.toList());

			return ResponseEntity.ok(Map.of("msg", "Get cart successfully", "data", response));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("msg", "Failed to retrieve cart", "error", e.getMessage()));
		}
	}

	@PostMapping("/cart")
	public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> request) {
		String userId = (String) request.get("userId");
		String productId = (String) request.get("productId");
		int quantity = Integer.parseInt(request.get("quantity").toString());
		String size = (String) request.get("size");
		boolean isMultiple = request.containsKey("isMultiple") && (boolean) request.get("isMultiple");

		// Find existing cart item: need specific find method
		// Repository doesn't have findByUserIdAndProductIdAndSize yet. 
		// We can fetch all by User and filter, or add method to Repo on the fly?
		// Better to simple filter in memory or Custom query.
		// Let's rely on fetching user cart (assuming small)
		List<Cart> userCarts = cartRepository.findByUserId(userId);
		Optional<Cart> existing = userCarts.stream()
				.filter(c -> c.getProductId().equals(productId) && c.getSize().equals(size))
				.findFirst();

		Cart cart;
		if (existing.isPresent()) {
			cart = existing.get();
			if (isMultiple) {
				cart.setQuantity(quantity);
			} else {
				cart.setQuantity(cart.getQuantity() + quantity);
			}
		} else {
			cart = Cart.builder()
					.userId(userId)
					.productId(productId)
					.quantity(quantity)
					.size(size)
					.build();
		}
		
		cartRepository.save(cart);
		return ResponseEntity.status(201).body(Map.of("msg", "Add to cart successfully"));
	}
	
	@PostMapping("/cart/decrease")
	public ResponseEntity<?> decreaseCart(@RequestBody Map<String, Object> request) {
		String userId = (String) request.get("userId");
		String productId = (String) request.get("productId");
		int quantity = Integer.parseInt(request.get("quantity").toString());
		
		List<Cart> userCarts = cartRepository.findByUserId(userId);
		// Note: Node.js logic only checks productId logic in `findOne({ userId, productId })`. 
		// It IGNORES size? Node code: `const cart = await Cart.findOne({ userId, productId });`.
		// If user has multiple rows with same ProductID but different Size, Node code picks ONE (First match) and reduces it.
		// THIS IS A BUG in original, but I must follow logic "preserve 100% logic".
		
		Optional<Cart> existing = userCarts.stream()
				.filter(c -> c.getProductId().equals(productId))
				.findFirst();

		if (existing.isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("msg", "Cart not found"));
		}
		
		Cart cart = existing.get();
		if (cart.getQuantity() == 1) { // Or quantity <= reduceAmount? Node says if quantity === 1 then delete.
			cartRepository.delete(cart);
			return ResponseEntity.ok(cart);
		}
		
		cart.setQuantity(cart.getQuantity() - quantity);
		cartRepository.save(cart);
		
		return ResponseEntity.ok(Map.of("msg", "Decrease cart successfully", "data", cart));
	}
	
	@DeleteMapping("/cart/deleteItem")
	public ResponseEntity<?> deleteItemCart(@RequestBody Map<String, String> request) {
		String userId = request.get("userId");
		String productId = request.get("productId");
		
		if (userId == null || productId == null) {
			return ResponseEntity.badRequest().body(Map.of("msg", "Missing productId or userId"));
		}

		// Same logic: Node deletes ONE item matching userId and productId
		List<Cart> userCarts = cartRepository.findByUserId(userId);
		Optional<Cart> existing = userCarts.stream()
				.filter(c -> c.getProductId().equals(productId))
				.findFirst();
		
		if (existing.isPresent()) {
			cartRepository.delete(existing.get());
			return ResponseEntity.ok(Map.of("msg", "Delete cart successfully"));
		} else {
			return ResponseEntity.status(404).body(Map.of("msg", "Cart not found"));
		}
	}
	
	@DeleteMapping("/cart/delete/")
	public ResponseEntity<?> deleteAllCart(@RequestBody Map<String, String> request) {
		String userId = request.get("userId");
		if (userId == null) return ResponseEntity.badRequest().body(Map.of("msg", "Missing userId"));
		
		List<Cart> carts = cartRepository.findByUserId(userId);
		cartRepository.deleteAll(carts);
		
		// Node.js returns: res.send(cart) which is the delete result { acknowledged: true, deletedCount: N }
		// We return a similar map structure to satisfy frontend expectations if it relies on "deletedCount" or simply object existence
		return ResponseEntity.ok(Map.of("acknowledged", true, "deletedCount", carts.size())); 
	}
}
