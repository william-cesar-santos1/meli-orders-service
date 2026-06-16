package br.com.meli.orders.application.acl;

import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.billing.PaymentStatus;
import org.springframework.stereotype.Component;

// SOLUÇÃO: Anti-Corruption Layer (ACL) — traduz a linguagem do contexto de Billing
// para a linguagem do contexto de Order. Isola o contexto de orders de mudancas
// no modelo de billing. Se o contexto de billing alterar seu enum PaymentStatus,
// apenas este tradutor precisa ser atualizado — o dominio de orders permanece inalterado.
// Principio: ACL (DDD) previne que a linguagem de um contexto "contamine" outro.
@Component
public class BillingPaymentTranslator {

    /**
     * Converte o estado de pagamento do contexto de Billing para o
     * estado do pedido no contexto de Orders.
     *
     * @param paymentStatus estado retornado pelo servico de billing
     * @return estado equivalente no contexto de orders
     */
    public OrderStatus translate(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case CAPTURED -> OrderStatus.PAID;
            case FAILED -> OrderStatus.CANCELLED;
            case REFUNDED -> OrderStatus.CANCELLED;
            // TODO (exercício): implementar traducao de PARTIALLY_REFUNDED.
            // Qual estado do Order melhor representa um reembolso parcial?
            // Sugestao: criar OrderStatus.PARTIALLY_CANCELLED e tratar no dominio.
            case PARTIALLY_REFUNDED -> OrderStatus.CANCELLED; // TODO: substituir por PARTIALLY_CANCELLED
            default -> OrderStatus.CREATED;
        };
    }
}

