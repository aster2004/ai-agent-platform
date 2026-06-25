package com.ai.agentplatform.module.codegen.config;

import com.ai.agentplatform.module.user.config.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 代码生成模块安全配置
 * <p>JWT 鉴权由成员 1 的 {@link JwtAuthFilter} 统一提供，
 * 开发阶段接口放行（permitAll），无 Token 时 CurrentUserUtil 兜底返回 userId=1，
 * 正式环境只需将 .permitAll() 改回 .authenticated() 即可。</p>
 */
@Configuration
public class CodeGenSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public CodeGenSecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain codeGenFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/codegen/**")
                .csrf(csrf -> csrf.disable())
                // 注册成员 1 的 JwtAuthFilter，使 codegen 接口也能解析 JWT Token
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 开发阶段全部放行：无 Token 兜底 userId=1；有 Token 解析真实用户
                        .requestMatchers("/api/codegen/**").permitAll()
                );
        return http.build();
    }
}