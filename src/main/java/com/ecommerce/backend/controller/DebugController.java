package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Product;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class DebugController {

	@Autowired
	ProductRepository productRepository;
	
	@Autowired
	MongoTemplate mongoTemplate;

	@GetMapping("/debug/products")
	public ResponseEntity<?> debugProducts() {
		Map<String, Object> debug = new HashMap<>();
		
		// Collection name
		String collectionName = mongoTemplate.getCollectionName(Product.class);
		debug.put("collectionName", collectionName);
		
		// Test 1: Get ALL products (no filter)
		List<Product> allProducts = productRepository.findAll();
		debug.put("totalInDB", allProducts.size());
		
		// Test 2: Get products with deletedAt = null
		Query queryNull = new Query(Criteria.where("deletedAt").is(null));
		List<Product> withDeletedAtNull = mongoTemplate.find(queryNull, Product.class);
		debug.put("deletedAt_is_null", withDeletedAtNull.size());
		
		// Test 3: Get products without deletedAt field
		Query queryNotExists = new Query(Criteria.where("deletedAt").exists(false));
		List<Product> withoutDeletedAt = mongoTemplate.find(queryNotExists, Product.class);
		debug.put("deletedAt_not_exists", withoutDeletedAt.size());
		
		// Test 4: Get products with deletedAt != null
		Query queryNotNull = new Query(Criteria.where("deletedAt").ne(null));
		List<Product> withDeletedAtNotNull = mongoTemplate.find(queryNotNull, Product.class);
		debug.put("deletedAt_not_null", withDeletedAtNotNull.size());
		
		// Test 5: Sample product
		if (!allProducts.isEmpty()) {
			Product sample = allProducts.get(0);
			Map<String, Object> sampleInfo = new HashMap<>();
			sampleInfo.put("id", sample.getId());
			sampleInfo.put("name", sample.getName());
			sampleInfo.put("deletedAt", sample.getDeletedAt());
			sampleInfo.put("createdAt", sample.getCreatedAt());
			sampleInfo.put("hasDeletedAtField", sample.getDeletedAt() != null ? "yes" : "null_or_missing");
			debug.put("sampleProduct", sampleInfo);
		}
		
		return ResponseEntity.ok(debug);
	}
}
