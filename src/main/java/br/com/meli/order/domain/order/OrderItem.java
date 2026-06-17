package br.com.meli.order.domain.order;

import java.math.BigDecimal;

public record OrderItem(
        String id,
        String productId,
        int quantity,
        BigDecimal unitPrice,
        String productName
) {}
