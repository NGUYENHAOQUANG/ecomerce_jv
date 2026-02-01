package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.Category;
import com.ecommerce.backend.model.Product;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class ProductController {

	@Autowired
	ProductRepository productRepository;

	@Autowired
	CategoryRepository categoryRepository;
	
	@Autowired
	MongoTemplate mongoTemplate;

	@PostMapping("/product")
	public ResponseEntity<?> createProduct(@RequestBody Product productRequest) {
		try {
			// Resolve Category Name if Type is provided or Category ID is provided
			String categoryName = productRequest.getType();
			String categoryId = productRequest.getCategory();

			if (categoryId != null) {
				Optional<Category> cat = categoryRepository.findById(categoryId);
				if (cat.isPresent()) {
					categoryName = cat.get().getName();
				}
			}

			Product product = Product.builder()
					.name(productRequest.getName())
					.price(productRequest.getPrice())
					.description(productRequest.getDescription())
					.type(categoryName)
					.category(categoryId)
					.size(productRequest.getSize())
					.material(productRequest.getMaterial())
					.images(productRequest.getImages())
					.build();

			return ResponseEntity.status(201).body(Map.of("message", "Thêm sản phẩm thành công", "data", productRepository.save(product)));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
		}
	}

	@GetMapping("/product")
	public ResponseEntity<?> getProduct(
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(required = false) Integer limit,
			@RequestParam(defaultValue = "0") String sortType,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String category) {

		// Logic for matchQuery
		Criteria criteria = null;
		
		if ("all".equals(status)) {
			// No deletedAt filter - leave criteria as null
		} else if ("inactive".equals(status)) {
			criteria = Criteria.where("deletedAt").ne(null);
		} else {
			// Active: deletedAt is null
			criteria = Criteria.where("deletedAt").is(null);
		}

		if (category != null && !category.isEmpty()) {
			if (criteria == null) {
				criteria = Criteria.where("category").is(category);
			} else {
				criteria.and("category").is(category);
			}
		}

		// Logic for Sort
		Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
		switch (sortType) {
			case "1": sort = Sort.by(Sort.Direction.ASC, "createdAt"); break;
			case "4": sort = Sort.by(Sort.Direction.ASC, "price"); break;
			case "5": sort = Sort.by(Sort.Direction.DESC, "price"); break;
			default: break; // 0
		}

		int pageSize = (limit != null) ? limit : 10;
		if (limit == null) pageSize = 1000; // Default logical limit if not specified? Node code has "limitNumber ? ... : null".
		// Actually MongoTemplate query needs a limit. 
		
		Pageable pageable = PageRequest.of(Math.max(0, page - 1), pageSize, sort);

		// Build query based on whether we have criteria
		Query query;
		long total;
		List<Product> products;
		
		if (criteria == null) {
			// No filters - get all
			query = new Query().with(pageable);
			total = mongoTemplate.count(new Query(), Product.class);
			products = mongoTemplate.find(query, Product.class);
		} else {
			query = new Query(criteria).with(pageable);
			total = mongoTemplate.count(new Query(criteria), Product.class);
			products = mongoTemplate.find(query, Product.class);
		}
		
		// TODO: Lookup orders to "hasOrders" field. 
		// Node.js does a lookup to see if product is in any order.
		// Skipping for now -> returning clean Entity.

		// Enrich with category info?
		// Nodejs: unwinds categoryInfo.
		// Spring Data: we have categoryId. 
		// If Frontend needs full Category object inside, we might need DTO. 
		// But returning Product entity which has fields matching JSON is usually okay if mapped filtering.
		// Actually Product entity `category` is a String ID. The frontend might expect populated object?
		// Node.js: `category` field remains ID, but `categoryInfo` is added.
		// I'll stick to returning Product as is for now.

		Map<String, Object> response = new HashMap<>();
		response.put("contents", products);
		response.put("total", total);
		response.put("page", page);
		response.put("limit", limit);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/product/{productId}")
	public ResponseEntity<?> getDetailProduct(@PathVariable String productId) {
		// Only finding active
		Optional<Product> productOpt = productRepository.findById(productId);
		if (productOpt.isPresent()) {
			Product p = productOpt.get();
			if (p.getDeletedAt() == null) {
				return ResponseEntity.ok(p);
			}
		}
		return ResponseEntity.status(404).body(Map.of("message", "Sản phẩm không tồn tại hoặc đã ngừng kinh doanh"));
	}
	
	@GetMapping("/product/search")
	public ResponseEntity<?> searchProducts(@RequestParam String query, @RequestParam(required = false) String category) {
		if (query == null || query.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập từ khóa."));
		}

		TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(query);
		Query q = TextQuery.queryText(textCriteria);
		q.addCriteria(Criteria.where("deletedAt").is(null));
		
		if (category != null && !category.equals("all") && !category.equals("All")) {
			q.addCriteria(Criteria.where("category").is(category));
		}
		
		q.with(PageRequest.of(0, 20));
		
		List<Product> products = mongoTemplate.find(q, Product.class);
		
		return ResponseEntity.ok(Map.of("contents", products, "total", products.size()));
	}
	
	@GetMapping("/related-products/{productId}")
	public ResponseEntity<?> getRelatedProducts(@PathVariable String productId) {
		Optional<Product> currentOpt = productRepository.findById(productId);
		if (currentOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Not found"));
		
		Product current = currentOpt.get();
		
		// Build query criteria
		Criteria criteria = Criteria.where("id").ne(productId)
			.and("deletedAt").is(null);
		
		if (current.getCategory() != null && !current.getCategory().isEmpty()) {
			criteria.and("category").is(current.getCategory());
		} else if (current.getType() != null && !current.getType().isEmpty()) {
			// Fallback to type if no category
			criteria.and("type").is(current.getType());
		}
		
		Query query = new Query(criteria).limit(5);
		List<Product> related = mongoTemplate.find(query, Product.class);
		
		return ResponseEntity.ok(Map.of("relatedProducts", related));
	}
	
	@GetMapping("/products/wishlist")
	public ResponseEntity<?> getWishlist(@RequestParam String ids) {
		if (ids == null || ids.isEmpty()) return ResponseEntity.ok(Collections.emptyList());
		
		List<String> idList = Arrays.asList(ids.split(","));
		List<Product> products = productRepository.findByIdInAndDeletedAtNull(idList);
		return ResponseEntity.ok(products);
	}

	@PutMapping("/product/{productId}")
	public ResponseEntity<?> updateProduct(@PathVariable String productId, @RequestBody Product updates) {
		Optional<Product> prodOpt = productRepository.findById(productId);
		if (prodOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Sản phẩm không tồn tại"));
		
		Product product = prodOpt.get();
		
		// Map updates
		if (updates.getName() != null) product.setName(updates.getName());
		if (updates.getPrice() > 0) product.setPrice(updates.getPrice());
		if (updates.getDescription() != null) product.setDescription(updates.getDescription());
		if (updates.getMaterial() != null) product.setMaterial(updates.getMaterial());
		if (updates.getImages() != null) product.setImages(updates.getImages());
		if (updates.getSize() != null) product.setSize(updates.getSize());
		
		if (updates.getCategory() != null) {
			product.setCategory(updates.getCategory());
			categoryRepository.findById(updates.getCategory()).ifPresent(cat -> product.setType(cat.getName()));
		}

		return ResponseEntity.ok(Map.of("message", "Cập nhật thành công", "data", productRepository.save(product)));
	}

	@DeleteMapping("/product/{productId}")
	public ResponseEntity<?> deleteProduct(@PathVariable String productId) {
		// Logic: if ordered -> soft delete (update deletedAt)
		// if not ordered -> hard delete
		// Checking order existence (TODO: need Order repo)
		
		// For now assuming always ordered to be safe, OR mostly soft delete.
		// Node.js logic:
		// const isOrdered = await Order.findOne({ "items.productId": productId });
		// if (isOrdered) ...
		
		// I will default to soft delete for safety until Order module is ready.
		// Or perform hard delete if I can confirm? Use soft delete logic.
		
		return productRepository.findById(productId).map(p -> {
			if (p.getDeletedAt() != null) {
				// Restore
				p.setDeletedAt(null);
				productRepository.save(p);
				return ResponseEntity.ok(Map.of("message", "Đã mở bán lại sản phẩm thành công."));
			} else {
				// Delete
				p.setDeletedAt(new Date());
				productRepository.save(p);
				return ResponseEntity.ok(Map.of("message", "Đã chuyển sang trạng thái 'Ngừng kinh doanh'."));
			}
		}).orElse(ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy sản phẩm")));
	}
}
