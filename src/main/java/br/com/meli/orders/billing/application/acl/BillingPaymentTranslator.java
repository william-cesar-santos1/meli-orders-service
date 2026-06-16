package br.com.meli.orders.billing.application.acl;

import br.com.meli.orders.billing.domain.PaymentStatus;
import br.com.meli.orders.order.domain.OrderStatus;

/**
 * Anti-Corruption Layer (ACL): traduz a linguagem do contexto de Billing
 * para a linguagem do contexto de Order.
 * Isola o contexto de orders de mudanças no modelo de billing.
 *
 * POJO puro — sem anotações de framework. Registrado como @Bean em UseCaseConfig.
 * Principio: ACL (DDD) previne que a linguagem de um contexto "contamine" outro.
 */
public class BillingPaymentTranslator {

    public OrderStatus translate(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case CAPTURED -> OrderStatus.PAID;
            case FAILED, REFUNDED, PARTIALLY_REFUNDED -> OrderStatus.CANCELLED;
            default -> OrderStatus.CREATED;
        };
    }
}
