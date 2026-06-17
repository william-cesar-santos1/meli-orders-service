package br.com.meli.order.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotencyFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    public IdempotencyFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String key = request.getHeader("Idempotency-Key");

        if (key == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String cached = redisTemplate.opsForValue().get("idempotency:response:" + key);
        if (cached != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(cached);
            return;
        }

        Boolean registered = redisTemplate.opsForValue()
                .setIfAbsent("idempotency:lock:" + key, "processing", Duration.ofMinutes(5));

        if (Boolean.FALSE.equals(registered)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapped);

        if (wrapped.getStatus() / 100 == 2) {
            String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
            redisTemplate.opsForValue().set(
                    "idempotency:response:" + key, body, Duration.ofHours(24)
            );
        }

        wrapped.copyBodyToResponse();
    }
}

