package br.com.meli.order.application.acl;

import br.com.meli.order.domain.order.OrderStatus;

/**
 * Anti-Corruption Layer (ACL): traduz o resultado de pagamento recebido do serviço
 * externo de Billing para a linguagem ubíqua do contexto de Order.
 *
 * Isola o contexto de Order de mudanças no modelo ou contrato do Billing externo.
 * POJO puro — sem anotações de framework. Registrado como @Bean em UseCaseConfig.
 *
 * Princípio: ACL (DDD) — a linguagem de um contexto externo não contamina o modelo interno.
 */
public class BillingPaymentTranslator {

    public OrderStatus translate(PaymentResult result) {
        return switch (result) {
            case CAPTURED -> OrderStatus.PAID;
            case FAILED, REFUNDED, PARTIALLY_REFUNDED -> OrderStatus.CANCELLED;
            default -> OrderStatus.CREATED;
        };
    }
}

