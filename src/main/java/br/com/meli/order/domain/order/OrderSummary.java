package br.com.meli.order.domain.order;

import java.math.BigDecimal;

// SOLUÇÃO: Value Object de leitura — representa um resumo leve do pedido
// sem carregar o agregado completo (sem itens, sem metadados de auditoria).
// Usado por FindOrderSummaryPort para retornar apenas os campos necessarios
// em consultas de listagem, evitando o carregamento desnecessario do grafo inteiro.
// Principio: CQRS / Read Model — o modelo de leitura e separado do agregado de escrita.
public record OrderSummary(
        Long orderId,
        String customerId,
        String status,
        BigDecimal total
) {}

