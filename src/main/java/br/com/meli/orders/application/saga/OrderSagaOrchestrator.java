package br.com.meli.orders.application.saga;

import br.com.meli.orders.api.BillingClient;
import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.CreateOrderUseCase;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.billing.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// SOLUÇÃO: Saga Orchestrator para o fluxo de criacao e pagamento de pedido.
// Implementa o Saga Pattern com compensacao: se qualquer passo falhar,
// os passos anteriores sao desfeitos (compensados), mantendo a consistencia.
//
// Fluxo:
//   OrderPlaced -> ChargeRequested -> (PaymentConfirmed | PaymentFailed)
//   -> confirmar pedido (PAID) | cancelar pedido (CANCELLED)
//
// Principio: Saga Pattern garante consistencia eventual em transacoes distribuidas
// sem usar transacoes distribuidas (2PC), que sao frageis e limitantes em microsservicos.
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final CreateOrderUseCase createOrderUseCase;
    private final BillingClient billingClient;
    private final OrderRepositoryPort orderRepository;

    public OrderSagaOrchestrator(CreateOrderUseCase createOrderUseCase,
                                  BillingClient billingClient,
                                  OrderRepositoryPort orderRepository) {
        this.createOrderUseCase = createOrderUseCase;
        this.billingClient = billingClient;
        this.orderRepository = orderRepository;
    }

    /**
     * Executa a saga de criacao e pagamento do pedido com compensacao.
     *
     * @param request dados do pedido a ser criado
     * @return pedido no estado final (PAID ou CANCELLED)
     */
    public Order execute(CreateOrderRequest request) {
        // Passo 1: criar o pedido (OrderPlaced)
        Order order = createOrderUseCase.execute(request);
        log.info("Saga: OrderPlaced orderId={} customerId={}", order.id(), order.customerId());

        try {
            // Passo 2: solicitar cobranca (ChargeRequested)
            // SOLUÇÃO: BillingClient agora tem CircuitBreaker e timeout via Resilience4j.
            // Se o billing-service estiver fora do ar, o circuito abre apos falhas consecutivas
            // e retorna imediatamente com FAILED — sem bloquear threads por minutos.
            log.info("Saga: ChargeRequested orderId={} amount={}", order.id(), order.totalAmount());
            PaymentStatus paymentResult = billingClient.charge(order.id(), order.totalAmount());

            // Passo 3: confirmar ou compensar com base no resultado
            if (PaymentStatus.CAPTURED.equals(paymentResult)) {
                // Caminho feliz: PaymentConfirmed -> PAID
                log.info("Saga: PaymentConfirmed orderId={} -> PAID", order.id());
                // TODO (exercicio): implementar idempotencia no consumo de PaymentConfirmed.
                // Se este evento chegar duplicado (at-least-once delivery), a segunda
                // atualizacao deve ser ignorada. Sugestao: verificar status atual antes de atualizar.
                return orderRepository.updateStatus(order.id(), OrderStatus.PAID);
            } else {
                // Caminho de falha: PaymentFailed -> compensar (cancelar pedido)
                return compensate(order, "PaymentFailed: status=" + paymentResult);
            }

        } catch (Exception e) {
            // SOLUÇÃO: qualquer excecao (timeout, circuit open, erro de rede) aciona compensacao.
            // O pedido e cancelado com motivo de negocio, evitando pedidos orfaos.
            log.error("Saga: excecao durante cobranca orderId={} — iniciando compensacao", order.id(), e);
            return compensate(order, "Excecao durante cobranca: " + e.getMessage());
        }
    }

    /**
     * Compensacao: cancela o pedido quando o pagamento falha.
     * SOLUÇÃO: em falha irreversivel de pagamento, o pedido e cancelado com motivo de negocio.
     * O cliente pode ser notificado para tentar novamente.
     */
    private Order compensate(Order order, String reason) {
        log.warn("Saga: compensacao acionada orderId={} motivo={}", order.id(), reason);
        Order cancelled = orderRepository.updateStatus(order.id(), OrderStatus.CANCELLED);
        log.info("Saga: pedido cancelado orderId={} status={}", order.id(), cancelled.status());
        return cancelled;
    }
}

