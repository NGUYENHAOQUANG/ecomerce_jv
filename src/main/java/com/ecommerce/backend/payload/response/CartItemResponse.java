package com.ecommerce.backend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private String name;
    private double price;
    private int quantity;
    private String size;
    private String sku;
    private double total;
    private List<String> images;
    private String productId;
    private String userId;
}
