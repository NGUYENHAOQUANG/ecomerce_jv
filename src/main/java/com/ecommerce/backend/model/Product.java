package com.ecommerce.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TextScore;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Document(collection = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

	@TextIndexed(weight = 2)
	private String name;

	private double price;

	@TextIndexed // Included in text search
	private String description;
	
	// type often stores Category Name in legacy Node Code, but we should rely on category ID ref
	private String type; 

	private String category; // Reference to Category ID (UUID String)

	private List<Size> size;

	private String material;

	private List<String> images;

	// Helper for Text Search Score
	@TextScore
	private Float score;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Size {
		private String name;
		private String amount; // Kept as String to match JSON data "1000"
	}
}
