package com.company.curtainwall.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                // 1. 禁用 CSRF (保持你原有的设置)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 启用 CORS (关键修改！)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeHttpRequests(auth -> auth
                        // 3. 修改路径匹配 (关键修改！)
                        // 加上 /api/ 前缀，同时保留不带 /api/ 的，以防万一
                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/api/models/**", "/models/**").permitAll()
                        .requestMatchers("/api/benchmarks/**", "/benchmarks/**").permitAll()
                        .requestMatchers("/api/corrosion/**", "/corrosion/**").permitAll()
                        .requestMatchers("/images/**", "/api/images/**").permitAll()

                        // 4. 其他所有请求需要认证
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 5. 定义 CORS 配置 Bean (新增)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许的来源：允许 localhost (Nginx) 和 localhost:3000 (前端调试)
        configuration.setAllowedOrigins(Arrays.asList("http://localhost", "http://localhost:3000", "http://127.0.0.1"));
        // 允许的方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许的头信息
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 允许携带凭证 (Cookie 等)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}