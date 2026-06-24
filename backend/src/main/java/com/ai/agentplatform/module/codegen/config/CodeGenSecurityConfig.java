package com.ai.agentplatform.module.codegen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class CodeGenSecurityConfig {

    @Bean
    public SecurityFilterChain codeGenFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/codegen/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 放行swagger文档，不需要登录
                        .requestMatchers("/api/codegen/**").permitAll()
                        .requestMatchers("/api/codegen/v3/api-docs", "/api/codegen/swagger-ui/**").permitAll()
                        // 所有生成、历史接口必须登录
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}