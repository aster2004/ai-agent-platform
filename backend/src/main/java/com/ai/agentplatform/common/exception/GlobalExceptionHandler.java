package com.ai.agentplatform.common.exception;

import com.ai.agentplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, WebRequest request) {
        if (isSseRequest(request)) {
            return sseErrorResponse(e.getCode(), e.getMessage());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object handleValidationException(Exception e, WebRequest request) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().hasFieldErrors()) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (isSseRequest(request)) {
            return sseErrorResponse(400, message);
        }
        return Result.fail(400, message);
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, WebRequest request) {
        log.error("系统异常", e);
        if (isSseRequest(request)) {
            return sseErrorResponse(500, "系统内部错误");
        }
        return Result.fail("系统内部错误");
    }

    private boolean isSseRequest(WebRequest request) {
        if (!(request instanceof ServletWebRequest servletWebRequest)) {
            return false;
        }
        String uri = servletWebRequest.getRequest().getRequestURI();
        return uri != null && uri.endsWith("/stream");
    }

    private ResponseEntity<String> sseErrorResponse(int code, String message) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", message,
                    "code", code
            ));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body("data: " + payload + "\n\n");
        } catch (Exception ex) {
            log.error("构造 SSE 错误响应失败", ex);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body("data: {\"type\":\"error\",\"message\":\"系统内部错误\"}\n\n");
        }
    }
}
