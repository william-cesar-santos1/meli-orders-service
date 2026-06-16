package br.com.meli.orders.order.application.saga;

import br.com.meli.orders.billing.application.acl.BillingPaymentTranslator;
import br.com.meli.orders.billing.application.port.out.BillingPort;
import br.com.meli.orders.billing.domain.PaymentStatus;
import br.com.meli.orders.order.application.CreateOrderUseCase;
import br.com.meli.orders.order.application.PlaceOrderCommand;
import br.com.meli.orders.order.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saga Orchestrator para o fluxo de criação e pagamento de pedido.
 * Implementa o Saga Pattern com compensação: se qualquer passo falhar,
 * os passos anteriores são desfeitos (compensados), mantendo a consistência.
 *
 * Fluxo:
 *   OrderPlaced -> ChargeRequested -> (PaymentConfirmed | PaymentFailed)
 *   -> confirmar pedido (PAID) | cancelar pedido (CANCELLED)
 *
 * POJO puro — sem anotações de framework.
 * Depende de BillingPort (interface), não de BillingHttpAdapter (implementação concreta).
 * Principio: Dependency Inversion + Saga Pattern.
 */
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final CreateOrderUseCase createOrderUseCase;
    private final BillingPort billingPort;
    private final OrderRepositoryPort orderRepository;
    private final BillingPaymentTranslator billingTranslator;

    public OrderSagaOrchestrator(CreateOrderUseCase createOrderUseCase,
                                  BillingPort billingPort,
                                  OrderRepositoryPort orderRepository,
                                  BillingPaymentTranslator billingTranslator) {
        this.createOrderUseCase = createOrderUseCase;
        this.billingPort = billingPort;
        this.orderRepository = orderRepository;
        this.billingTranslator = billingTranslator;
    }

    /**
     * Executa a saga de criação e pagamento do pedido com compensação.
     */
    public Order execute(PlaceOrderCommand command) {
        // Passo 1: criar o pedido (OrderPlaced)
        Order order = createOrderUseCase.execute(command);
        log.info("Saga: OrderPlaced orderId={} customerId={}", order.id(), order.customerId());

        try {
            // Passo 2: solicitar cobrança (ChargeRequested)
            // BillingHttpAdapter tem CircuitBreaker e timeout via Resilience4j.
            // Se o billing-service estiver fora do ar, o circuito abre após falhas consecutivas
            // e retorna imediatamente com FAILED — sem bloquear threads.
            log.info("Saga: ChargeRequested orderId={} amount={}", order.id(), order.totalAmount());
            PaymentStatus paymentResult = billingPort.charge(order.id(), order.totalAmount());

            // Passo 3: traduzir resultado via ACL e confirmar ou compensar
            OrderStatus targetStatus = billingTranslator.translate(paymentResult);

            if (OrderStatus.PAID.equals(targetStatus)) {
                // Caminho feliz: PaymentConfirmed -> PAID
                log.info("Saga: PaymentConfirmed orderId={} -> PAID", order.id());
                return orderRepository.updateStatus(order.id(), OrderStatus.PAID);
            } else {
                // Caminho de falha: PaymentFailed -> compensar (cancelar pedido)
                return compensate(order, "PaymentFailed: billingStatus=" + paymentResult);
            }

        } catch (Exception e) {
            // Qualquer exceção (timeout, circuit open, erro de rede) aciona compensação.
            log.error("Saga: exceção durante cobrança orderId={} — iniciando compensação", order.id(), e);
            return compensate(order, "Exceção durante cobrança: " + e.getMessage());
        }
    }

    /**
     * Compensação: cancela o pedido quando o pagamento falha ou lança exceção.
     * Garante que não existam pedidos órfãos no sistema.
     */
    private Order compensate(Order order, String reason) {
        log.warn("Saga: compensação acionada orderId={} motivo={}", order.id(), reason);
        Order cancelled = orderRepository.updateStatus(order.id(), OrderStatus.CANCELLED);
        log.info("Saga: pedido cancelado orderId={} status={}", order.id(), cancelled.status());
        return cancelled;
    }
}
