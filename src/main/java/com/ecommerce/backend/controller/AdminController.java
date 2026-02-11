package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Order;
import com.ecommerce.backend.model.Product;
import com.ecommerce.backend.model.User;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ProductRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.security.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
// @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')") // Handled in SecurityConfig or manually
public class AdminController {

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	MongoTemplate mongoTemplate;
	
	@Autowired
	PasswordEncoder passwordEncoder;

	// --- 1. DASHBOARD STATS ---
	@GetMapping("/stats")
	public ResponseEntity<?> getDashboardStats() {
		try {
			// Criteria for Revenue: (Paid or Completed or Delivered) AND Not Deleted
			Criteria revenueCondition = new Criteria().andOperator(
					Criteria.where("deletedAt").is(null),
					new Criteria().orOperator(
							Criteria.where("paymentStatus").in("paid", "completed"),
							Criteria.where("deliveryStatus").is("delivered")
					)
			);

			// 1. Total Revenue
			Aggregation totalRevAgg = newAggregation(
					match(revenueCondition),
					group().sum("totalAmount").as("total")
			);
			AggregationResults<Map> totalRevRes = mongoTemplate.aggregate(totalRevAgg, Order.class, Map.class);
			double totalRevenue = totalRevRes.getUniqueMappedResult() != null ? ((Number) totalRevRes.getUniqueMappedResult().get("total")).doubleValue() : 0;

			// 2. Daily Revenue (Using DateOperators for safety)
			Aggregation dailyAgg = newAggregation(
					match(revenueCondition),
					project("totalAmount", "createdAt")
						.and(org.springframework.data.mongodb.core.aggregation.DateOperators.dateOf("createdAt")
								.withTimezone(org.springframework.data.mongodb.core.aggregation.DateOperators.Timezone.valueOf("+07:00"))
								.toString("%Y-%m-%d")).as("dateStr"),
					group("dateStr")
						.sum("totalAmount").as("total")
						.count().as("orders"),
					sort(Sort.Direction.ASC, "_id")
			);
			AggregationResults<Map> dailyRes = mongoTemplate.aggregate(dailyAgg, Order.class, Map.class);
			List<Map> dailyRevenue = dailyRes.getMappedResults();

			// 3. Monthly Revenue (Using DateOperators for safety)
			Aggregation monthlyAgg = newAggregation(
					match(revenueCondition),
					project("totalAmount", "createdAt")
						.and(org.springframework.data.mongodb.core.aggregation.DateOperators.dateOf("createdAt")
								.withTimezone(org.springframework.data.mongodb.core.aggregation.DateOperators.Timezone.valueOf("+07:00"))
								.month()).as("month"),
					group("month")
						.sum("totalAmount").as("total")
						.count().as("orders"),
					sort(Sort.Direction.ASC, "_id")
			);
			AggregationResults<Map> monthlyRes = mongoTemplate.aggregate(monthlyAgg, Order.class, Map.class);
			List<Map> monthlyRevenue = monthlyRes.getMappedResults();

			// 4. Order Status Stats (Active orders)
			Aggregation statusAgg = newAggregation(
					match(Criteria.where("deletedAt").is(null)),
					group("deliveryStatus").count().as("count")
			);
			AggregationResults<Map> statusRes = mongoTemplate.aggregate(statusAgg, Order.class, Map.class);
			List<Map> orderStatusStats = statusRes.getMappedResults();

			// 5. Top Products (Best Selling)
			Aggregation topProdAgg = newAggregation(
					match(Criteria.where("deletedAt").is(null)),
					unwind("items"),
					group("items.productId")
						.sum("items.quantity").as("totalSold")
						.sum(
							org.springframework.data.mongodb.core.aggregation.ArithmeticOperators.Multiply.valueOf("items.price").multiplyBy("items.quantity")
						).as("revenue"),
					sort(Sort.Direction.DESC, "totalSold"),
					limit(5),
					lookup("products", "_id", "_id", "productInfo"),
					unwind("productInfo"),
					project("totalSold", "revenue")
						.and("productInfo.name").as("name")
						.and("productInfo.price").as("price")
						.and(org.springframework.data.mongodb.core.aggregation.ArrayOperators.ArrayElemAt.arrayOf("productInfo.images").elementAt(0)).as("image")
			);
			AggregationResults<Map> topProdRes = mongoTemplate.aggregate(topProdAgg, Order.class, Map.class);
			List<Map> topProducts = topProdRes.getMappedResults();

			// 6. Recent Orders
			Query recentQ = new Query(Criteria.where("deletedAt").is(null));
			recentQ.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(5);
			recentQ.fields().include("firstName", "lastName", "totalAmount", "deliveryStatus", "createdAt");
			List<Order> recentOrders = mongoTemplate.find(recentQ, Order.class);

			// 7. Counts (Correctly filtering deletedAt: null)
			long totalUsers = userRepository.countByRole("user");
			long totalProducts = mongoTemplate.count(new Query(Criteria.where("deletedAt").is(null)), Product.class); 
			long totalOrders = mongoTemplate.count(new Query(Criteria.where("deletedAt").is(null)), Order.class);

			Map<String, Object> response = new HashMap<>();
			response.put("totalRevenue", totalRevenue);
			response.put("dailyRevenue", dailyRevenue);
			response.put("monthlyRevenue", monthlyRevenue);
			response.put("orderStatusStats", orderStatusStats);
			response.put("recentOrders", recentOrders);
			response.put("topProducts", topProducts);
			response.put("counts", Map.of("users", totalUsers, "products", totalProducts, "orders", totalOrders));

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
		}
	}

	// --- 2. USER MANAGEMENT ---
	@GetMapping("/users")
	public ResponseEntity<?> getAllUsers(@RequestParam(defaultValue = "1") int page,
										 @RequestParam(defaultValue = "10") int limit,
										 @RequestParam(required = false) String search,
										 @RequestParam(required = false) String authType,
										 @RequestParam(required = false) String status) {
		Criteria criteria = Criteria.where("role").is("user");
		if (search != null && !search.isEmpty()) {
			criteria.orOperator(
					Criteria.where("username").regex(search, "i"),
					Criteria.where("email").regex(search, "i")
			);
		}
		if (authType != null && !authType.equals("all")) criteria.and("authType").is(authType);
		if (status != null && !status.equals("all")) {
			if (status.equals("active")) criteria.and("isBlocked").is(false);
			if (status.equals("blocked")) criteria.and("isBlocked").is(true);
		}

		Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"));
		long total = mongoTemplate.count(query, User.class);
		query.with(org.springframework.data.domain.PageRequest.of(page - 1, limit));
		List<User> users = mongoTemplate.find(query, User.class);

		return ResponseEntity.ok(Map.of("contents", users, "total", total, "page", page, "limit", limit));
	}
	
	@PutMapping("/users/block/{userId}")
	public ResponseEntity<?> toggleBlockUser(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String userId) {
		User user = userRepository.findById(userId).orElse(null);
		if (user == null) return ResponseEntity.status(404).body(Map.of("message", "User not found"));
		
		if (user.getId().equals(userDetails.getId())) return ResponseEntity.badRequest().body(Map.of("message", "Không thể tự khóa chính mình"));
		if (user.getRole().equals("super_admin")) return ResponseEntity.status(403).body(Map.of("message", "Không thể khóa tài khoản Super Admin"));

		user.setBlocked(!user.isBlocked());
		userRepository.save(user);

		return ResponseEntity.ok(Map.of("message", "Đã " + (user.isBlocked() ? "khóa" : "mở khóa") + " tài khoản thành công", "isBlocked", user.isBlocked()));
	}

	// --- 3. MANAGER MANAGEMENT ---
	@GetMapping("/managers")
	public ResponseEntity<?> getAllAdmins(@RequestParam(defaultValue = "1") int page,
										  @RequestParam(defaultValue = "10") int limit,
										  @RequestParam(required = false) String search) {
		Criteria criteria = Criteria.where("role").in("admin", "super_admin");
		if (search != null && !search.isEmpty()) criteria.and("username").regex(search, "i");
		
		Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"));
		long total = mongoTemplate.count(query, User.class);
		query.with(org.springframework.data.domain.PageRequest.of(page - 1, limit));
		
		return ResponseEntity.ok(Map.of("contents", mongoTemplate.find(query, User.class), "total", total, "page", page, "limit", limit));
	}
	
	@PostMapping("/managers")
	public ResponseEntity<?> createAdmin(@RequestBody User request) {
		if (userRepository.findByUsername(request.getUsername()).isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
		}
		User admin = User.builder()
				.username(request.getUsername())
				.password(passwordEncoder.encode(request.getPassword()))
				.role(request.getRole() != null ? request.getRole() : "admin")
				.authType("local")
				.build();
		userRepository.save(admin);
		return ResponseEntity.status(201).body(Map.of("message", "Admin created successfully"));
	}

	// --- 4. PRODUCT MANAGEMENT ---
	@GetMapping("/products")
	public ResponseEntity<?> getAllProductsAdmin(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int limit,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") String sortType,
			@RequestParam(required = false) String category) {
		
		Criteria criteria = new Criteria();
		if ("active".equals(status)) criteria.and("deletedAt").is(null);
		else if ("inactive".equals(status)) criteria.and("deletedAt").ne(null);
		
		if (category != null && !category.equals("all")) criteria.and("category").is(category);
		
		if (search != null && !search.isEmpty()) {
			criteria.orOperator(Criteria.where("name").regex(search, "i"));
		}

		Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
		
		Query query = new Query(criteria).with(sort);
		long total = mongoTemplate.count(query, Product.class);
		query.with(org.springframework.data.domain.PageRequest.of(page - 1, limit));
		List<Product> products = mongoTemplate.find(query, Product.class);

		List<String> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
		List<String> orderedIds = mongoTemplate.findDistinct(
				new Query(Criteria.where("items.productId").in(productIds)), 
				"items.productId", Order.class, String.class);
		Set<String> orderedSet = new HashSet<>(orderedIds);
		
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

		List<Map<String, Object>> content = products.stream().map(p -> {
			Map<String, Object> map = mapper.convertValue(p, Map.class);
			map.put("hasOrders", orderedSet.contains(p.getId()));
			return map;
		}).collect(Collectors.toList());
		
		return ResponseEntity.ok(Map.of("contents", content, "total", total, "page", page, "limit", limit));
	}

	// --- 5. ORDER MANAGEMENT ---
	@GetMapping("/orders")
	public ResponseEntity<?> getAllOrders(@RequestParam(defaultValue = "1") int page,
										  @RequestParam(defaultValue = "10") int limit,
										  @RequestParam(required = false) String search,
										  @RequestParam(required = false) String status) {
		Criteria criteria = Criteria.where("deletedAt").is(null);
		if (status != null && !status.equals("all")) criteria.and("deliveryStatus").is(status);
		if (search != null && !search.isEmpty()) {
			criteria.orOperator(
				Criteria.where("firstName").regex(search, "i"),
				Criteria.where("email").regex(search, "i"),
				Criteria.where("phone").regex(search, "i")
			);
		}
		
		Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"));
		long total = mongoTemplate.count(query, Order.class);
		query.with(org.springframework.data.domain.PageRequest.of(page - 1, limit));
		List<Order> orders = mongoTemplate.find(query, Order.class);
		
		return ResponseEntity.ok(Map.of("contents", orders, "total", total, "page", page, "limit", limit));
	}
	
	@PutMapping("/orders/{orderId}")
	public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId, @RequestBody Map<String, String> body) {
		String deliveryStatus = body.get("deliveryStatus");
		Order order = orderRepository.findById(orderId).orElse(null);
		if (order == null) return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
		
		order.setDeliveryStatus(deliveryStatus);
		
		// Auto update payment logic
		if ("cancelled".equals(deliveryStatus)) {
			order.setPaymentStatus("cancelled");
		} else if ("delivered".equals(deliveryStatus)) {
			if (!"completed".equals(order.getPaymentStatus())) {
				order.setPaymentStatus("paid");
				if (order.getPaymentInfo().getPaidAt() == null) {
					order.getPaymentInfo().setPaidAt(new Date());
				}
			}
		} else {
			// Reset to pending if COD and not completed
			if ("cod".equals(order.getPaymentMethods())) {
				order.setPaymentStatus("pending");
			}
		}
		
		orderRepository.save(order);
		return ResponseEntity.ok(Map.of("message", "Update status successfully", "data", order));
	}

	@DeleteMapping("/managers/{userId}")
	public ResponseEntity<?> deleteAdmin(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String userId) {
		if (userId.equals(userDetails.getId())) {
			return ResponseEntity.badRequest().body(Map.of("message", "Cannot delete yourself"));
		}
		userRepository.deleteById(userId);
		return ResponseEntity.ok(Map.of("message", "Admin deleted successfully"));
	}

	@PutMapping("/managers/{userId}")
	public ResponseEntity<?> updateAdmin(@PathVariable String userId, @RequestBody User updates) {
		User admin = userRepository.findById(userId).orElse(null);
		if (admin == null) return ResponseEntity.status(404).body(Map.of("message", "Admin not found"));

		if (updates.getUsername() != null) admin.setUsername(updates.getUsername());
		if (updates.getPassword() != null && !updates.getPassword().trim().isEmpty()) {
			admin.setPassword(passwordEncoder.encode(updates.getPassword()));
		}
		if (updates.getRole() != null) admin.setRole(updates.getRole());

		userRepository.save(admin);
		return ResponseEntity.ok(Map.of("message", "Admin updated successfully", "data", admin));
	}

	@PutMapping("/managers/block/{userId}")
	public ResponseEntity<?> toggleBlockAdmin(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String userId) {
		User user = userRepository.findById(userId).orElse(null);
		if (user == null) return ResponseEntity.status(404).body(Map.of("message", "User not found"));
		
		if (user.getId().equals(userDetails.getId())) return ResponseEntity.badRequest().body(Map.of("message", "Không thể tự khóa chính mình"));
		if (user.getRole().equals("super_admin")) return ResponseEntity.status(403).body(Map.of("message", "Không thể khóa tài khoản Super Admin"));

		user.setBlocked(!user.isBlocked());
		userRepository.save(user);

		return ResponseEntity.ok(Map.of("message", "Đã " + (user.isBlocked() ? "khóa" : "mở khóa") + " tài khoản thành công", "isBlocked", user.isBlocked()));
	}
}
