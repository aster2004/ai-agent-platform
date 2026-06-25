package com.ai.agentplatform.module.codegen.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

/**
 * 代码生成模块内部 RestTemplate 配置
 * 用于调用同应用内其他模块接口（如成员2的 App 模块）
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 模块间内部调用专用 RestTemplate
     * 自动从当前请求上下文提取 JWT Token 转发，非 Web 上下文（如后台线程）兜底使用 Mock Token
     */
    @Bean
    public RestTemplate codeGenRestTemplate(
            RestTemplateBuilder builder,
            @Value("${server.port:8080}") int serverPort
    ) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String authHeader = extractCurrentAuthHeader();
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
            request.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return execution.execute(request, body);
        };

        return builder
                .rootUri("http://localhost:" + serverPort)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .interceptors(List.of(authInterceptor))
                .build();
    }

    /**
     * 从当前请求上下文提取 Authorization 头（真实 JWT Token）
     * 非 Web 上下文（如后台线程/定时任务）返回 Mock Token 兜底
     */
    private String extractCurrentAuthHeader() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest currentRequest = attrs.getRequest();
                String authHeader = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && !authHeader.isBlank()) {
                    return authHeader;
                }
            }
        } catch (Exception ignored) {
            // 非 Web 上下文或无当前请求，使用兜底
        }
        return "Bearer dev-mock-token";
    }
}
