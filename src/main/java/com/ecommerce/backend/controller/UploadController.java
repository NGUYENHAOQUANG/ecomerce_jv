package com.ecommerce.backend.controller;

import com.ecommerce.backend.service.ImageKitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class UploadController {

	@Autowired
	private ImageKitService imageKitService;

	@PostMapping("/upload")
	public ResponseEntity<?> uploadImage(@RequestParam("image") MultipartFile file) {
		System.out.println("=== Upload Request Received ===");
		System.out.println("File Name: " + (file != null ? file.getOriginalFilename() : "NULL"));
		System.out.println("File Size: " + (file != null ? file.getSize() : 0) + " bytes");
		System.out.println("Content Type: " + (file != null ? file.getContentType() : "NULL"));
		
		try {
			if (file == null || file.isEmpty()) {
				System.err.println("Upload Error: No file provided");
				return ResponseEntity.badRequest().body(Map.of("message", "Không có file nào được gửi lên"));
			}

			String fileName = "product_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
			System.out.println("Generated File Name: " + fileName);
			
			Map<String, Object> result = imageKitService.uploadImage(file.getBytes(), fileName, "/products");

			System.out.println("Upload Success! URL: " + result.get("url"));
			
			return ResponseEntity.ok(Map.of(
					"message", "Upload thành công",
					"url", result.get("url"),
					"fileId", result.get("fileId")
			));
		} catch (Exception e) {
			System.err.println("Upload Controller Error: " + e.getMessage());
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(Map.of(
				"message", "Lỗi khi upload ảnh lên ImageKit", 
				"error", e.getMessage()
			));
		}
	}
}
