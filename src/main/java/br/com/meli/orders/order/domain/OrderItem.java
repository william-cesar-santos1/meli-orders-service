package br.com.meli.orders.order.domain;

import java.math.BigDecimal;

public record OrderItem(
        String id,
        String productId,
        int quantity,
        BigDecimal unitPrice,
        String productName
) {}
