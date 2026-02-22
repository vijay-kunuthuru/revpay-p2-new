package com.revpay.config;

import com.revpay.model.entity.IdempotencyKey;
import com.revpay.repository.IdempotencyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
            String idempotencyKey = request.getHeader("Idempotency-Key");

            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                if (idempotencyRepository.existsById(idempotencyKey)) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Duplicate request detected.\",\"data\":null}");
                    return false;
                }
                
                idempotencyRepository.save(new IdempotencyKey(idempotencyKey, request.getRequestURI(), java.time.LocalDateTime.now()));
            }
        }
        return true;
    }
}