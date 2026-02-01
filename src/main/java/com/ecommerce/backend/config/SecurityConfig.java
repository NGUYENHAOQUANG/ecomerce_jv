package com.ecommerce.backend.config;

import com.ecommerce.backend.security.jwt.AuthEntryPointJwt;
import com.ecommerce.backend.security.jwt.AuthTokenFilter;
import com.ecommerce.backend.security.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    AuthEntryPointJwt unauthorizedHandler;
    
    @Autowired
    AuthTokenFilter authTokenFilter; // Spring tự tìm Bean từ @Component

    // --- ĐÃ XÓA HÀM authenticationJwtTokenFilter() GÂY LỖI ---

    // Hàm này giúp ngăn Filter chạy 2 lần (một lần trong Security, một lần mặc định)
    // Giờ nó sẽ hoạt động tốt vì chỉ còn duy nhất 1 bean AuthTokenFilter
    @Bean
    public FilterRegistrationBean<AuthTokenFilter> registration(AuthTokenFilter filter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> 
                    auth.requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ping").permitAll()
                        .requestMatchers("/api/product/**").permitAll() // Product routes
                        .requestMatchers("/api/products/**").permitAll() // Wishlist
                        .requestMatchers("/api/related-products/**").permitAll()
                        .requestMatchers("/api/category/**").permitAll() // Category routes
                        .requestMatchers("/api/trending-keywords").permitAll()
                        .requestMatchers("/api/payment/sepay-callback").permitAll()
                        .requestMatchers("/api/upload").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyAuthority("admin", "super_admin")
                        .anyRequest().authenticated()
                );

        http.authenticationProvider(authenticationProvider());

        // Sử dụng trực tiếp biến authTokenFilter đã Autowired ở trên
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}