package br.com.meli.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// PROBLEMA: Order carrega paymentStatus diretamente no agregado.
// PaymentStatus eh um conceito do contexto de Billing — nao de Order.
// Misturar os dois contextos no mesmo agregado viola o principio de
// Bounded Contexts (DDD): cada contexto deve ter linguagem ubiqua propria
// e nao deve depender do modelo de outro contexto. Isso aumenta o acoplamento
// entre servicos e dificulta a evolucao independente de cada contexto.
public record Order(
        Long id,
        String customerId,
        List<OrderItem> items,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        PaymentStatus paymentStatus  // PROBLEMA: campo de Billing vazando para o contexto de Order
) {
    public Order {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static Order create(String customerId, List<OrderItem> items) {
        BigDecimal total = items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // PROBLEMA: PaymentStatus.PENDING injetado na criacao do pedido —
        // forcando Order a conhecer o ciclo de vida de Billing desde o inicio.
        return new Order(null, customerId, List.copyOf(items), OrderStatus.CREATED, total, Instant.now(), PaymentStatus.PENDING);
    }

    public Order withId(Long id) {
        return new Order(id, customerId, items, status, totalAmount, createdAt, paymentStatus);
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, customerId, items, newStatus, totalAmount, createdAt, paymentStatus);
    }

    public Order withPaymentStatus(PaymentStatus newPaymentStatus) {
        // PROBLEMA: mutacao de estado de Billing diretamente no agregado Order —
        // responsabilidade que deveria estar no contexto de Billing.
        return new Order(id, customerId, items, status, totalAmount, createdAt, newPaymentStatus);
    }
}
