package br.com.meli.order.infrastructure.filter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

// SOLUÇÃO: filtro Servlet que propaga X-Request-Id para MDC — todos os logs herdam.
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_MDC = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        try {
            // SOLUÇÃO: extrair correlationId do header ou gerar novo UUID.
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            // SOLUÇÃO: por no MDC — todos os logs desta thread herdam automaticamente.
            MDC.put(CORRELATION_ID_MDC, correlationId);
            
            // SOLUÇÃO: echo correlationId no header de resposta para cliente rastrear.
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            filterChain.doFilter(request, response);
        } finally {
            // SOLUÇÃO: limpar MDC ao fim da requisicao — evita leak em thread pools.
            MDC.clear();
        }
    }
}
