package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Cart;
import com.ecommerce.backend.model.Order;
import com.ecommerce.backend.model.Product;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ProductRepository;
import com.ecommerce.backend.security.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class OrderController {

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	CartRepository cartRepository;

	@Autowired
	ProductRepository productRepository;

	@PostMapping("/order")
	public ResponseEntity<?> createOrder(@AuthenticationPrincipal UserDetailsImpl userDetails, @RequestBody Order orderRequest) {
		try {
			String userId = userDetails.getId();
			// Validate fields manually or use logic
			if (orderRequest.getFirstName() == null || orderRequest.getEmail() == null || orderRequest.getPaymentMethods() == null) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Vui lòng điền đầy đủ thông tin bắt buộc"));
			}

			List<Cart> cartItems = cartRepository.findByUserId(userId);
			// Filter deletedAt null manually if repo returns all
			cartItems = cartItems.stream().filter(c -> c.getDeletedAt() == null).collect(Collectors.toList());

			if (cartItems.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Giỏ hàng trống"));
			}

			double totalAmount = 0;
			List<Order.OrderItem> orderItems = new ArrayList<>();

			for (Cart cartItem : cartItems) {
				Optional<Product> pOpt = productRepository.findById(cartItem.getProductId());
				if (pOpt.isEmpty() || pOpt.get().getDeletedAt() != null) {
					return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Sản phẩm với ID " + cartItem.getProductId() + " không tồn tại"));
				}
				Product product = pOpt.get();
				double itemTotal = product.getPrice() * cartItem.getQuantity();
				totalAmount += itemTotal;

				orderItems.add(Order.OrderItem.builder()
						.productId(cartItem.getProductId())
						.quantity(cartItem.getQuantity())
						.size(cartItem.getSize())
						.price(product.getPrice())
						.build());
			}

			Order newOrder = Order.builder()
					.userId(userId)
					.firstName(orderRequest.getFirstName())
					.lastName(orderRequest.getLastName())
					.companyName(orderRequest.getCompanyName())
					.country(orderRequest.getCountry())
					.street(orderRequest.getStreet())
					.apartment(orderRequest.getApartment())
					.cities(orderRequest.getCities())
					.state(orderRequest.getState())
					.phone(orderRequest.getPhone())
					.zipCode(orderRequest.getZipCode())
					.email(orderRequest.getEmail())
					.deliveryStatus("pending")
					.paymentMethods(orderRequest.getPaymentMethods())
					.paymentStatus(orderRequest.getPaymentStatus())
					.items(orderItems)
					.totalAmount(totalAmount)
					.build();

			Order savedOrder = orderRepository.save(newOrder);

			// Soft delete cart items
			Date now = new Date();
			cartItems.forEach(c -> {
				c.setDeletedAt(now);
				cartRepository.save(c); // Or saveAll
			});

			return ResponseEntity.status(201).body(Map.of(
					"success", true,
					"message", "Đơn hàng đã được tạo thành công",
					"data", savedOrder
			));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Lỗi server khi tạo đơn hàng", "error", e.getMessage()));
		}
	}

	@GetMapping("/orders")
	public ResponseEntity<?> getOrdersByUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
		List<Order> orders = orderRepository.findByUserId(userDetails.getId());
		// Sort by createdAt desc manually if repo doesn't
		orders.sort(Comparator.comparing(Order::getCreatedAt).reversed());
		return ResponseEntity.ok(Map.of("success", true, "data", orders));
	}

	@GetMapping("/order/{orderId}")
	public ResponseEntity<?> getOrderById(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String orderId) {
		return orderRepository.findById(orderId)
				.filter(o -> o.getUserId().equals(userDetails.getId()) && o.getDeletedAt() == null)
				.map(o -> ResponseEntity.ok(Map.of("success", true, "data", o)))
				.orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Không tìm thấy đơn hàng"))); // Type mismatch? Map is object
	}

	@DeleteMapping("/order/{orderId}")
	public ResponseEntity<?> deleteOrder(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String orderId) {
		Optional<Order> orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isPresent()) {
			Order order = orderOpt.get();
			if (order.getUserId().equals(userDetails.getId()) && order.getDeletedAt() == null) {
				order.setDeliveryStatus("cancelled");
				order.setPaymentStatus("cancelled");
				orderRepository.save(order);
				return ResponseEntity.ok(Map.of("success", true, "message", "Xóa đơn hàng thành cong"));
			}
		}
		return ResponseEntity.status(404).body(Map.of("success", false, "message", "Không tìm thấy đơn hàng"));
	}

	@PutMapping("/order/{orderId}")
	public ResponseEntity<?> updateOrder(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String orderId, @RequestBody Order updates) {
		Optional<Order> orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isPresent()) {
			Order order = orderOpt.get();
			if (!order.getUserId().equals(userDetails.getId()) || order.getDeletedAt() != null) {
				return ResponseEntity.status(404).body(Map.of("success", false, "message", "Không tìm thấy đơn hàng"));
			}
			
			if (!"pending".equals(order.getDeliveryStatus())) {
				return ResponseEntity.status(403).body(Map.of("success", false, "message", "Không thể chỉnh sửa đơn hàng đã được xử lý"));
			}
			
			// Updates
			if (updates.getFirstName() != null) order.setFirstName(updates.getFirstName());
			if (updates.getLastName() != null) order.setLastName(updates.getLastName());
			if (updates.getPhone() != null) order.setPhone(updates.getPhone());
			if (updates.getEmail() != null) order.setEmail(updates.getEmail());
			if (updates.getStreet() != null) order.setStreet(updates.getStreet());
			if (updates.getApartment() != null) order.setApartment(updates.getApartment());
			if (updates.getCities() != null) order.setCities(updates.getCities());
			if (updates.getState() != null) order.setState(updates.getState());
			if (updates.getCountry() != null) order.setCountry(updates.getCountry());
			
			return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "Cập nhật thông tin đơn hàng thành công",
					"data", orderRepository.save(order)
			));
		}
		return ResponseEntity.status(404).body(Map.of("success", false, "message", "Không tìm thấy đơn hàng"));
	}
}
