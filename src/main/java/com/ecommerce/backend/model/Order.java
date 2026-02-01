package com.ecommerce.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Document(collection = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

	private String userId;
	private String firstName;
	private String lastName;
	@Builder.Default
	private String companyName = "";
	private String country;
	private String street;
	@Builder.Default
	private String apartment = "";
	private String cities;
	private String state;
	private String phone;
	private String zipCode;
	private String email;

	@Builder.Default
	private String deliveryStatus = "pending"; // pending, processing, shipped, delivered, cancelled

	private String paymentMethods; // cod, qr

	@Builder.Default
	private String paymentStatus = "pending"; // pending, paid, failed, cancelled

	private List<OrderItem> items;

	private double totalAmount;

	@Builder.Default
	private PaymentInfo paymentInfo = new PaymentInfo();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class OrderItem {
		private String productId;
		private int quantity;
		private String size;
		private double price;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class PaymentInfo {
		private Number sepayTransactionId;
		private String gateway;
		private String transactionDate;
		private String accountNumber;
		private Double transferAmount;
		private String referenceCode;
		private String content;
		private Date paidAt;
	}
}
