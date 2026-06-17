package br.com.meli.order.infrastructure.billing;

import br.com.meli.order.application.acl.PaymentResult;
import br.com.meli.order.application.port.out.BillingPort;
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

/**
 * Adapter de saída: cliente HTTP para o serviço externo de Billing (simulado via WireMock no docker-compose).
 *
 * Implementa {@link BillingPort} com:
 * - CircuitBreaker (Resilience4j): após N falhas, abre o circuito e retorna fallback imediatamente.
 * - Retry: tenta novamente em erros transitórios antes de propagar a falha.
 * - Timeout explícito: protege o pool de threads do Tomcat de dependências lentas.
 *
 * Mapeia a resposta HTTP do serviço externo para {@link PaymentResult} (tipo ACL do contexto de Order),
 * garantindo que nenhum modelo externo "vaze" para as camadas de application ou domain.
 *
 * Princípio: Adapter Pattern + Dependency Inversion + ACL boundary.
 */
@Component
public class BillingHttpAdapter implements BillingPort {

    private static final Logger log = LoggerFactory.getLogger(BillingHttpAdapter.class);

    private final RestTemplate restTemplate;

    @Value("${services.billing.url:http://localhost:8082}")
    private String billingServiceUrl;

    public BillingHttpAdapter(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(500))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    @CircuitBreaker(name = "billing", fallbackMethod = "billingFallback")
    @Retry(name = "billing")
    public PaymentResult charge(Long orderId, BigDecimal amount) {
        String url = billingServiceUrl + "/payments/charge";
        ChargeRequest request = new ChargeRequest(orderId, amount);
        log.info("BillingHttpAdapter: enviando cobrança orderId={} amount={}", orderId, amount);
        ChargeResponse response = restTemplate.postForObject(url, request, ChargeResponse.class);
        if (response != null) {
            return PaymentResult.valueOf(response.status());
        }
        return PaymentResult.FAILED;
    }

    /**
     * Fallback acionado quando o circuito está aberto ou todas as tentativas falham.
     * Retorna FAILED — o OrderSagaOrchestrator trata a compensação.
     */
    public PaymentResult billingFallback(Long orderId, BigDecimal amount, Exception ex) {
        log.warn("BillingHttpAdapter: circuito aberto ou falha total orderId={} — fallback ativado: {}",
                orderId, ex.getMessage());
        return PaymentResult.FAILED;
    }

    record ChargeRequest(Long orderId, BigDecimal amount) {}
    record ChargeResponse(String status) {}
}

