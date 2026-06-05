package br.com.meli.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
}
