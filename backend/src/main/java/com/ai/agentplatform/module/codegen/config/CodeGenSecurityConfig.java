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
                .securityMatcher("/api/codegen/**") // 只匹配你的代码生成接口
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // 全部匿名放行
                );
        return http.build();
    }
}