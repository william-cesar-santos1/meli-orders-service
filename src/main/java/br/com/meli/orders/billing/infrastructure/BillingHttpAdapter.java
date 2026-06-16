package br.com.meli.orders.billing.infrastructure;

import br.com.meli.orders.billing.application.port.out.BillingPort;
import br.com.meli.orders.billing.domain.PaymentStatus;
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
 * Adapter de saída: adapter HTTP para o serviço de billing externo.
 * Implementa BillingPort (definida na camada de application) com:
 * - CircuitBreaker (Resilience4j): após N falhas, abre o circuito e retorna fallback imediatamente.
 * - Retry: tenta novamente em erros transitorios antes de propagar a falha.
 * - Timeout explícito: protege o pool de threads do Tomcat de dependências lentas.
 *
 * Principio: Adapter Pattern + Dependency Inversion.
 * Fica em infrastructure — completamente isolado das camadas de application e domain.
 */
@Component
public class BillingHttpAdapter implements BillingPort {

    private static final Logger log = LoggerFactory.getLogger(BillingHttpAdapter.class);

    private final RestTemplate restTemplate;

    @Value("${services.billing.url:http://billing-service:8082}")
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
    public PaymentStatus charge(Long orderId, BigDecimal amount) {
        String url = billingServiceUrl + "/payments/charge";
        ChargeRequest request = new ChargeRequest(orderId, amount);
        log.info("BillingHttpAdapter: enviando cobranca orderId={} amount={}", orderId, amount);
        ChargeResponse response = restTemplate.postForObject(url, request, ChargeResponse.class);
        if (response != null) {
            return PaymentStatus.valueOf(response.status());
        }
        return PaymentStatus.FAILED;
    }

    /**
     * Fallback acionado quando o circuito está aberto ou todas as tentativas falham.
     * Retorna FAILED — o OrderSagaOrchestrator trata a compensação.
     */
    public PaymentStatus billingFallback(Long orderId, BigDecimal amount, Exception ex) {
        log.warn("BillingHttpAdapter: circuito aberto ou falha total orderId={} — fallback ativado: {}",
                orderId, ex.getMessage());
        return PaymentStatus.FAILED;
    }

    record ChargeRequest(Long orderId, BigDecimal amount) {}
    record ChargeResponse(String status) {}
}

