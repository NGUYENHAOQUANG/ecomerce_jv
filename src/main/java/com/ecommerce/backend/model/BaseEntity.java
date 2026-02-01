package com.ecommerce.backend.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import com.fasterxml.jackson.annotation.JsonProperty;


import java.util.Date;
import java.util.UUID;

@Data
public abstract class BaseEntity {

	@Id
	@JsonProperty("_id")
	private String id;

	@CreatedDate
	private Date createdAt;

	@LastModifiedDate
	private Date updatedAt;

	private Date deletedAt;

	public BaseEntity() {
		this.id = UUID.randomUUID().toString();
	}
}
