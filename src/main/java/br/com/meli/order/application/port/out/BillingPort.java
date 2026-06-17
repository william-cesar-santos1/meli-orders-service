package br.com.meli.order.application.port.out;

import br.com.meli.order.application.acl.PaymentResult;

import java.math.BigDecimal;

/**
 * Porta de saída do contexto de Order para o serviço externo de Billing.
 * Define o contrato que o contexto de Order usa para solicitar uma cobrança.
 *
 * A implementação concreta (BillingHttpAdapter) fica em order.infrastructure.billing,
 * totalmente desconhecida pela camada de application.
 *
 * O retorno é {@link PaymentResult}, tipo ACL do contexto de Order, que isola
 * o domínio de qualquer modelo de dados do serviço externo.
 *
 * Princípio: Dependency Inversion — application define a interface, infrastructure implementa.
 */
public interface BillingPort {
    PaymentResult charge(Long orderId, BigDecimal amount);
}

