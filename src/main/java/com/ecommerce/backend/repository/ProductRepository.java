package com.ecommerce.backend.repository;

import com.ecommerce.backend.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    // Find active products
    List<Product> findByDeletedAtNull();
    Page<Product> findByDeletedAtNull(Pageable pageable);
    
    // Find by Category (Active)
    Page<Product> findByCategoryAndDeletedAtNull(String category, Pageable pageable);
    
    // Find related products (by category, excluding current, active)
    List<Product> findByCategoryAndIdNotAndDeletedAtNull(String category, String id);
    
    // Find by list of IDs (Wishlist)
    List<Product> findByIdInAndDeletedAtNull(List<String> ids);
    
    // Full text search active products
    Page<Product> findAllBy(TextCriteria criteria, Pageable pageable);
    
    // Aggregations helper could be done here or in impl 
    boolean existsByCategory(String category);
    long countByCategory(String category);
    long countByCategoryAndDeletedAtIsNull(String category);
}
