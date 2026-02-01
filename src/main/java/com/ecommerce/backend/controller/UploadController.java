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
		try {
			if (file.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("message", "Không có file nào được gửi lên"));
			}

			String fileName = "product_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
			Map<String, Object> result = imageKitService.uploadImage(file.getBytes(), fileName, "/products");

			return ResponseEntity.ok(Map.of(
					"message", "Upload thành công",
					"url", result.get("url"),
					"fileId", result.get("fileId")
			));
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi khi upload ảnh lên ImageKit: " + e.getMessage()));
		}
	}
}
