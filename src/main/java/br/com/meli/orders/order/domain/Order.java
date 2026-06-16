package br.com.meli.orders.order.domain;

import br.com.meli.orders.order.domain.events.OrderPaid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// SOLUÇÃO: Order é o Aggregate Root do contexto de orders.
// Nao possui mais dependencia de tipos do contexto de Billing (ex: PaymentStatus removido).
// A comunicacao com billing ocorre via evento OrderPaid (domain.order.events),
// traduzido pela Anti-Corruption Layer (application.acl.BillingPaymentTranslator).
// Principio: Bounded Context Isolation (DDD) — cada contexto tem linguagem ubiqua propria.
public record Order(
        Long id,
        String customerId,
        List<OrderItem> items,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public Order {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static Order create(String customerId, List<OrderItem> items) {
        BigDecimal total = items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(null, customerId, List.copyOf(items), OrderStatus.CREATED, total, Instant.now());
    }

    public Order withId(Long id) {
        return new Order(id, customerId, items, status, totalAmount, createdAt);
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, customerId, items, newStatus, totalAmount, createdAt);
    }

    // SOLUÇÃO: em vez de armazenar paymentStatus no agregado Order,
    // o agregado publica um evento de dominio (OrderPaid) quando o pagamento e confirmado.
    // O ACL (BillingPaymentTranslator) converte o evento externo de billing para este evento.
    public OrderPaid markAsPaid() {
        return new OrderPaid(this.id, this.customerId, this.totalAmount, Instant.now());
    }
}
