package com.ecommerce.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class ImageKitService {

	@Value("${imagekit.url.endpoint}")
	private String urlEndpoint;

	@Value("${imagekit.public.key}")
	private String publicKey;

	@Value("${imagekit.private.key}")
	private String privateKey;

	private final RestTemplate restTemplate = new RestTemplate();

	public Map<String, Object> uploadImage(byte[] fileBytes, String fileName, String folder) throws Exception {
		String url = "https://upload.imagekit.io/api/v1/files/upload";

		System.out.println("=== ImageKit Upload Debug ===");
		System.out.println("URL Endpoint: " + urlEndpoint);
		System.out.println("Public Key: " + publicKey);
		System.out.println("Private Key: " + (privateKey != null ? "***SET***" : "NULL"));
		System.out.println("File Name: " + fileName);
		System.out.println("File Size: " + fileBytes.length + " bytes");
		System.out.println("Folder: " + folder);

		// Create multipart form data
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		// Basic Auth
		String auth = privateKey + ":";
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
		headers.set("Authorization", "Basic " + encodedAuth);

		// Build form data
		org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("file", new org.springframework.core.io.ByteArrayResource(fileBytes) {
			@Override
			public String getFilename() {
				return fileName;
			}
		});
		body.add("fileName", fileName);
		if (folder != null && !folder.isEmpty()) {
			body.add("folder", folder);
		}
		body.add("publicKey", publicKey);

		HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

			System.out.println("ImageKit Response Status: " + response.getStatusCode());
			System.out.println("ImageKit Response Body: " + response.getBody());

			if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
				@SuppressWarnings("unchecked")
				Map<String, Object> result = response.getBody();
				return result;
			} else {
				throw new Exception("ImageKit upload failed: " + response.getStatusCode());
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			System.err.println("ImageKit HTTP Error: " + e.getStatusCode());
			System.err.println("ImageKit Error Body: " + e.getResponseBodyAsString());
			throw new Exception("ImageKit upload failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
		} catch (Exception e) {
			System.err.println("ImageKit General Error: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}
}
