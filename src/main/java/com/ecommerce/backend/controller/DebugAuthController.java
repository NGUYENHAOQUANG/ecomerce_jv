package com.ecommerce.backend.controller;

import com.ecommerce.backend.model.User;
import com.ecommerce.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugAuthController {
    
    @Autowired
    UserRepository userRepository;
    
    @Autowired
    PasswordEncoder encoder;
    
    @GetMapping("/check-password/{username}")
    public ResponseEntity<?> checkPassword(@PathVariable String username, @RequestParam String password) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "User not found"));
        }
        
        User user = userOpt.get();
        String storedPassword = user.getPassword();
        boolean matches = encoder.matches(password, storedPassword);
        
        return ResponseEntity.ok(Map.of(
            "username", username,
            "inputPassword", password,
            "storedPasswordHash", storedPassword,
            "storedPasswordLength", storedPassword != null ? storedPassword.length() : 0,
            "matches", matches,
            "isBcryptHash", storedPassword != null && storedPassword.startsWith("$2")
        ));
    }
}
