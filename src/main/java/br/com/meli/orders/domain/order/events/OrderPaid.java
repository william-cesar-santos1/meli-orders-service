package br.com.meli.orders.domain.order.events;

import java.math.BigDecimal;
import java.time.Instant;

// SOLUÇÃO: evento de dominio publicado pelo agregado Order quando o pagamento e confirmado.
// Representa um fato de negocio imutavel: "um pedido foi pago".
// O contexto de Billing comunica o resultado do pagamento via ACL,
// que traduz o evento externo para esta linguagem interna do contexto de orders.
// Principio: Domain Events (DDD) — desacoplamento entre contextos via eventos.
// Nota: Order (agregado) e seus tipos principais estao em br.com.meli.orders.domain
// (pacote raiz), para compatibilidade com a camada de infraestrutura JPA.
// A separacao de sub-pacotes (order/billing) sera completada em clean-architecture-8.
public record OrderPaid(
        Long orderId,
        String customerId,
        BigDecimal amount,
        Instant occurredAt
) {}

