package com.bugmonster.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.code, "message", ex.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> input(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT",
                "message", "请求格式错误，请检查字段、必填内容和长度；不接受 owner_id/ownerId"));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(DataAccessException ex) {
        LOG.error("Database operation failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "DATABASE_UNAVAILABLE", "message", "数据库暂不可用，请检查服务和初始化配置"));
    }
}
