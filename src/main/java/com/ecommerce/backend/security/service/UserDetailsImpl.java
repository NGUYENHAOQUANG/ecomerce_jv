package com.ecommerce.backend.security.service;

import com.ecommerce.backend.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;


@Data
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserDetailsImpl implements UserDetails {
	private static final long serialVersionUID = 1L;

	@EqualsAndHashCode.Include
	private String id;
	private String username;
	private String email;
	@JsonIgnore
	private String password;
	private String role;
	private boolean isBlocked;

	public static UserDetailsImpl build(User user) {
		// Map role to Authority. Assuming single role for now.
		// If more roles, logic changes.
		// Node code uses simple string "user", "admin", "super_admin".
		// Spring Security expects "ROLE_...". We can adapt.
		
		return new UserDetailsImpl(
				user.getId(),
				user.getUsername(),
				user.getEmail(),
				user.getPassword(),
				user.getRole(),
				user.isBlocked()
		);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// Return role as authority
		return Collections.singletonList(new SimpleGrantedAuthority(role));
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !isBlocked;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return !isBlocked; // Or use separate enabled field if exists
	}


}
