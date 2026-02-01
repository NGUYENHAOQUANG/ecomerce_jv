package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Order;
import com.ecommerce.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class PaymentController {

	@Autowired
	OrderRepository orderRepository;
	
	@Value("${sepay.api.key:YOUR_SEPAY_API_KEY}") // Should be in properties
	private String sepayApiKey;

	@PostMapping("/payment/sepay-callback")
	public ResponseEntity<?> sepayCallback(@RequestHeader(value = "Authorization", required = false) String authHeader, @RequestBody Map<String, Object> request) {
		try {
			String apiKey = authHeader != null ? authHeader.replace("Apikey ", "") : null;
			// Note: Node.js uses process.env.SEPAY_API_KEY or default.
			// Currently my application.properties doesn't have sepay.api.key. I should add it or use default.
			// assuming validation passes if matches configured key.
			// If not configured, use hardcoded email from Node.js code as fallback/example?
			// Node.js: process.env.SEPAY_API_KEY || "nguyenhaoquang2004@gmail.com" from .env
			
			String expectedApiKey = (sepayApiKey != null && !sepayApiKey.equals("YOUR_SEPAY_API_KEY")) ? sepayApiKey : "nguyenhaoquang2004@gmail.com";

			if (apiKey == null || !apiKey.equals(expectedApiKey)) {
				return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized - Invalid API key"));
			}

			String transferType = (String) request.get("transferType");
			if (!"in".equals(transferType)) {
				return ResponseEntity.ok(Map.of("success", true, "message", "Transaction type not supported"));
			}
			
			String code = (String) request.get("code");
			String content = (String) request.get("content");
			String orderId = null;

			if (code != null) {
				orderId = code;
			}
			
			if (orderId == null && content != null) {
				Pattern pattern = Pattern.compile("[a-fA-F0-9-]{24,36}");
				Matcher matches = pattern.matcher(content);
				if (matches.find()) {
					String capturedId = matches.group(0);
					if (capturedId.length() == 32 && !capturedId.contains("-")) {
						// Format 8-4-4-4-12
						capturedId = String.format("%s-%s-%s-%s-%s", 
								capturedId.substring(0, 8),
								capturedId.substring(8, 12),
								capturedId.substring(12, 16),
								capturedId.substring(16, 20),
								capturedId.substring(20));
					}
					orderId = capturedId;
				}
			}

			if (orderId == null) {
				return ResponseEntity.ok(Map.of("success", true, "message", "Payment received but cannot identify order"));
			}

			Order order = orderRepository.findById(orderId).orElse(null);
			if (order == null || order.getDeletedAt() != null) {
				return ResponseEntity.ok(Map.of("success", true, "message", "Order not found"));
			}
			
			// Check amount
			// transferAmount can be Integer or Double in JSON map
			double transferAmount = Double.parseDouble(request.get("transferAmount").toString());
			if (transferAmount < order.getTotalAmount()) {
				return ResponseEntity.ok(Map.of("success", true, "message", "Payment amount insufficient"));
			}

			order.setPaymentStatus("completed");
			order.setUpdatedAt(new Date());
			
			Order.PaymentInfo info = order.getPaymentInfo();
			if (info == null) info = new Order.PaymentInfo();
			
			info.setSepayTransactionId(request.get("id") instanceof Number ? (Number) request.get("id") : null);
			info.setGateway((String) request.get("gateway"));
			info.setTransactionDate((String) request.get("transactionDate"));
			info.setAccountNumber((String) request.get("accountNumber"));
			info.setTransferAmount(transferAmount);
			info.setReferenceCode((String) request.get("referenceCode"));
			info.setContent((String) request.get("content"));
			info.setPaidAt(new Date());
			
			order.setPaymentInfo(info);
			
			Order updatedOrder = orderRepository.save(order);

			return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "Payment processed successfully",
					"data", Map.of("orderId", updatedOrder.getId(), "paymentStatus", updatedOrder.getPaymentStatus())
			));

		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error", "error", e.getMessage()));
		}
	}
}
