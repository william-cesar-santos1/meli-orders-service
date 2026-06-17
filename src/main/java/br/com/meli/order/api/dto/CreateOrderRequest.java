package br.com.meli.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotBlank String productId,
            @Positive int quantity,
            @DecimalMin("0.01") BigDecimal unitPrice,
            String productName
    ) {}
}

