package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Category;
import com.ecommerce.backend.model.Order;
import com.ecommerce.backend.model.Product;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class CategoryController {

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	MongoTemplate mongoTemplate;

	@GetMapping("/category")
	public ResponseEntity<?> getCategories(@RequestParam(required = false) String search) {
		List<Category> categories = categoryRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
		
		List<Category> activeCategories = categories.stream()
				.filter(c -> c.getDeletedAt() == null)
				.collect(Collectors.toList());

		if (search != null && !search.isEmpty()) {
			String searchLower = search.toLowerCase();
			activeCategories = activeCategories.stream()
					.filter(c -> c.getName().toLowerCase().contains(searchLower) || 
							 (c.getDescription() != null && c.getDescription().toLowerCase().contains(searchLower)))
					.collect(Collectors.toList());
		}

		return ResponseEntity.ok(activeCategories);
	}

	@PostMapping("/category")
	public ResponseEntity<?> createCategory(@RequestBody Category categoryRequest) {
		if (categoryRepository.findByName(categoryRequest.getName()).isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Category already exists"));
		}

		Category category = Category.builder()
				.name(categoryRequest.getName())
				.description(categoryRequest.getDescription())
				.imageUrl(categoryRequest.getImageUrl() != null ? categoryRequest.getImageUrl() : "")
				.slug(categoryRequest.getName().toLowerCase().replace(" ", "-"))
				.build();

		return ResponseEntity.status(201).body(categoryRepository.save(category));
	}

	@PutMapping("/category/{id}")
	public ResponseEntity<?> updateCategory(@PathVariable String id, @RequestBody Category updates) {
		return categoryRepository.findById(id).map(category -> {
			category.setName(updates.getName());
			category.setDescription(updates.getDescription());
			category.setImageUrl(updates.getImageUrl());
			if (updates.getName() != null) {
				category.setSlug(updates.getName().toLowerCase().replace(" ", "-"));
			}
			return ResponseEntity.ok(categoryRepository.save(category));
		}).orElse(ResponseEntity.status(404).body(Category.builder().build()));
	}

	@DeleteMapping("/category/{id}")
	public ResponseEntity<?> deleteCategory(@PathVariable String id) {
		if (productRepository.countByCategory(id) > 0) {
			long count = productRepository.countByCategory(id);
			return ResponseEntity.badRequest().body(Map.of("message", 
					"Không thể xóa! Có " + count + " sản phẩm thuộc danh mục này. Vui lòng chuyển chúng sang danh mục khác trước khi xóa."));
		}

		return categoryRepository.findById(id).map(category -> {
			category.setDeletedAt(new Date());
			categoryRepository.save(category);
			return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
		}).orElse(ResponseEntity.status(404).body(Map.of("message", "Category not found")));
	}

	@GetMapping("/category/popular")
	public ResponseEntity<?> getPopularCategories() {
		try {
			// Step 1: Aggregate products with their sales data
			Aggregation productSalesAgg = newAggregation(
				lookup("orders", "_id", "items.productId", "salesData"),
				unwind("salesData", true),
				unwind("salesData.items", true),
				match(new Criteria().orOperator(
					Criteria.where("salesData.paymentStatus").in("paid", "completed"),
					Criteria.where("salesData.deliveryStatus").is("delivered")
				)),
				match(Criteria.where("salesData.items.productId").exists(true)),
				project()
					.and("category").as("category")
					.and("salesData.items.quantity").as("quantity")
					.and("salesData.items.productId").as("itemProductId")
					.and("_id").as("productId"),
				match(new Criteria().andOperator(
					Criteria.where("itemProductId").exists(true),
					Criteria.where("productId").exists(true)
				)),
				project()
					.and("category").as("category")
					.andExpression("cond(eq(toString(productId), itemProductId), quantity, 0)").as("validQuantity"),
				group("category").sum("validQuantity").as("totalSold")
			);

			AggregationResults<Map> results = mongoTemplate.aggregate(productSalesAgg, Product.class, Map.class);
			Map<String, Integer> categorySalesMap = new HashMap<>();
			
			for (Map result : results.getMappedResults()) {
				String catId = (String) result.get("_id");
				Integer sold = ((Number) result.get("totalSold")).intValue();
				categorySalesMap.put(catId, sold);
			}

			// Step 2: Get all active categories and enrich with sales data
			List<Category> allCategories = categoryRepository.findAll().stream()
				.filter(c -> c.getDeletedAt() == null)
				.collect(Collectors.toList());

			List<Map<String, Object>> enrichedCategories = allCategories.stream()
				.map(cat -> {
					Map<String, Object> map = new HashMap<>();
					map.put("_id", cat.getId());
					map.put("name", cat.getName());
					map.put("description", cat.getDescription());
					map.put("imageUrl", cat.getImageUrl());
					map.put("slug", cat.getSlug());
					map.put("totalSold", categorySalesMap.getOrDefault(cat.getId(), 0));
					return map;
				})
				.sorted((a, b) -> ((Integer) b.get("totalSold")).compareTo((Integer) a.get("totalSold")))
				.limit(5)
				.collect(Collectors.toList());

			return ResponseEntity.ok(enrichedCategories);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
		}
	}

	@GetMapping("/category/trending")
	public ResponseEntity<?> getTrendingKeywords() {
		try {
			// Get top 6 best-selling products and return their names as keywords
			Aggregation trendingAgg = newAggregation(
				match(Criteria.where("deletedAt").is(null)),
				lookup("orders", "_id", "items.productId", "sales"),
				unwind("sales", true),
				unwind("sales.items", true),
				project()
					.and("name").as("name")
					.and("_id").as("productId")
					.and("sales.items.productId").as("itemProductId")
					.and("sales.items.quantity").as("quantity"),
				project()
					.and("name").as("name")
					.andExpression("cond(eq(toString(productId), itemProductId), quantity, 0)").as("validQty"),
				group("name").sum("validQty").as("sold"),
				sort(Sort.Direction.DESC, "sold"),
				limit(6),
				project().andExpression("_id").as("name")
			);

			AggregationResults<Map> results = mongoTemplate.aggregate(trendingAgg, Product.class, Map.class);
			List<String> keywords = results.getMappedResults().stream()
				.map(m -> ((String) m.get("name")).toLowerCase())
				.collect(Collectors.toList());

			return ResponseEntity.ok(keywords);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
		}
	}
}
