package br.com.meli.orders.api;

import br.com.meli.orders.domain.PaymentStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

// PROBLEMA: gateway para servico externo de billing sem timeout explícito e sem
// circuit breaker. Se o servico de billing ficar lento (ex: 30s por requisicao),
// todas as threads da aplicacao ficam bloqueadas esperando resposta —
// levando a uma falha em cascata (cascade failure). O pool de threads do Tomcat
// se esgota e a aplicacao inteira para de responder, mesmo para endpoints
// que nao dependem de billing. Principio violado: bulkhead e circuit breaker
// (Patterns de Resiliencia). Sem timeout: uma dependencia lenta mata todo o servico.
// Sem circuit breaker: requisicoes continuam chegando mesmo quando billing esta fora do ar.
@Component
public class BillingClient {

    // PROBLEMA: RestTemplate padrao sem qualquer configuracao de timeout.
    // O timeout padrao do Java HttpURLConnection eh "infinito" (bloqueante).
    // Em producao, isso significa que uma unica requisicao lenta pode
    // manter uma thread bloqueada por minutos ou horas.
    private final RestTemplate restTemplate = new RestTemplate();

    private final String billingServiceUrl = "http://billing-service:8082";

    // PROBLEMA: sem @CircuitBreaker, sem @Retry, sem timeout configurado.
    // Se billing-service estiver fora do ar, cada chamada vai aguardar
    // ate o TCP timeout do SO (tipicamente 2 minutos) antes de falhar.
    public PaymentStatus charge(Long orderId, BigDecimal amount) {
        try {
            String url = billingServiceUrl + "/payments/charge";
            ChargeRequest request = new ChargeRequest(orderId, amount);
            // PROBLEMA: chamada REST sincrona e bloqueante para servico externo
            // sem circuit breaker, sem retry policy e sem fallback definido.
            ChargeResponse response = restTemplate.postForObject(url, request, ChargeResponse.class);
            if (response != null) {
                return PaymentStatus.valueOf(response.status());
            }
            return PaymentStatus.FAILED;
        } catch (Exception e) {
            // PROBLEMA: captura generica de excecao sem diferenciar erros recuperaveis
            // (timeout, 503) de erros definitivos (400, 422). Sem circuit breaker,
            // erros consecutivos nao abrem o circuito para proteger o sistema.
            return PaymentStatus.FAILED;
        }
    }

    record ChargeRequest(Long orderId, BigDecimal amount) {}
    record ChargeResponse(String status) {}
}

