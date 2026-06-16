package br.com.meli.orders.billing.application.port.out;

import br.com.meli.orders.billing.domain.PaymentStatus;

import java.math.BigDecimal;

/**
 * Porta de saída do contexto de Billing.
 * Define o contrato que o contexto de Order usa para cobrar um pagamento.
 * A implementação concreta (BillingHttpAdapter) fica na camada de infrastructure,
 * totalmente desconhecida pela camada de application.
 * Principio: Dependency Inversion — application define a interface, infrastructure implementa.
 */
public interface BillingPort {
    PaymentStatus charge(Long orderId, BigDecimal amount);
}

