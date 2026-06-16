package br.com.meli.orders.api;

import br.com.meli.orders.domain.billing.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

// SOLUÇÃO: BillingClient com circuit breaker (Resilience4j), timeout e retry configurados.
// Protege a aplicacao de falhas em cascata (cascade failure):
// - Timeout: limita o tempo de espera por uma resposta do billing-service.
// - Circuit Breaker: apos N falhas consecutivas, o circuito abre e retorna imediatamente
//   com fallback sem tentar chamar o servico — dando tempo para o billing se recuperar.
// - Retry: tenta novamente em erros transitorios (ex: 503) antes de abrir o circuito.
// Principio: Bulkhead + Circuit Breaker (Patterns de Resiliencia — Michael Nygard).
@Component
public class BillingClient {

    private static final Logger log = LoggerFactory.getLogger(BillingClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.billing.url:http://billing-service:8082}")
    private String billingServiceUrl;

    // SOLUÇÃO: timeout explícito de 2s — protege o pool de threads do Tomcat.
    // Sem timeout, uma dependencia lenta pode esgotar todas as threads da aplicacao.
    public BillingClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(500))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
    }

    // SOLUÇÃO: @CircuitBreaker com fallback — se billing-service falhar 5x consecutivas,
    // o circuito abre e chama billingFallback() imediatamente por 30s.
    // @Retry tenta 2x antes de contar como falha para o circuit breaker.
    @CircuitBreaker(name = "billing", fallbackMethod = "billingFallback")
    @Retry(name = "billing")
    public PaymentStatus charge(Long orderId, BigDecimal amount) {
        String url = billingServiceUrl + "/payments/charge";
        ChargeRequest request = new ChargeRequest(orderId, amount);
        log.info("BillingClient: enviando cobranca orderId={} amount={}", orderId, amount);
        ChargeResponse response = restTemplate.postForObject(url, request, ChargeResponse.class);
        if (response != null) {
            return PaymentStatus.valueOf(response.status());
        }
        return PaymentStatus.FAILED;
    }

    // SOLUÇÃO: fallback chamado quando o circuito esta aberto ou quando todas as tentativas falham.
    // Retorna FAILED imediatamente — o Saga Orchestrator trata a compensacao.
    public PaymentStatus billingFallback(Long orderId, BigDecimal amount, Exception ex) {
        log.warn("BillingClient: circuito aberto ou falha total orderId={} — fallback ativado: {}",
                orderId, ex.getMessage());
        return PaymentStatus.FAILED;
    }

    record ChargeRequest(Long orderId, BigDecimal amount) {}
    record ChargeResponse(String status) {}
}
