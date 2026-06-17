package br.com.meli.order.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comando de criação de pedido — substitui CreateOrderRequest (DTO de API) nos casos de uso.
 * A camada de application define seus próprios tipos de entrada (comandos),
 * independente de como são recebidos (REST, gRPC, mensageria, etc.).
 *
 * O OrderController converte CreateOrderRequest → PlaceOrderCommand antes de chamar o use case.
 * Principio: Dependency Inversion — use cases não conhecem DTOs HTTP.
 */
public record PlaceOrderCommand(
        String customerId,
        List<Item> items
) {
    public record Item(
            String productId,
            int quantity,
            BigDecimal unitPrice,
            String productName
    ) {}
}

