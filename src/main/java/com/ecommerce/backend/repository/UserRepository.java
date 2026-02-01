package com.ecommerce.backend.repository;

import com.ecommerce.backend.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
	Optional<User> findByUsername(String username);
	Optional<User> findByEmail(String email);
	Boolean existsByUsername(String username);
	Boolean existsByEmail(String email);
	long countByRole(String role);
	Optional<User> findByResetPasswordTokenAndResetPasswordExpiresGreaterThan(String resetPasswordToken, java.util.Date now);
}
