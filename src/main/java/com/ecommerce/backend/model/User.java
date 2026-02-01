package com.ecommerce.backend.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

	@Indexed(unique = true)
	private String username;

	private String password;

	@Builder.Default
	private String authType = "local"; // enum: local, google

	private String avatar;

	private String resetPasswordToken;
	private Date resetPasswordExpires;

	@Builder.Default
	private String role = "user"; // enum: user, admin, super_admin

	@Builder.Default
	private boolean isBlocked = false;

	@Builder.Default
	private String firstName = "";

	@Builder.Default
	private String lastName = "";

	@Builder.Default
	private String phone = "";

	@Builder.Default
	private String email = "";

	@Builder.Default
	private String street = "";

	@Builder.Default
	private String apartment = "";

	@Builder.Default
	private String cities = "";

	@Builder.Default
	private String state = "";

	@Builder.Default
	private String country = "Vietnam";

	@Builder.Default
	private String zipCode = "";
}
