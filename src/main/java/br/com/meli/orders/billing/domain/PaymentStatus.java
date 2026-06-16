package br.com.meli.orders.billing.domain;

// SOLUÇÃO: PaymentStatus agora pertence ao contexto de Billing, dentro de domain.billing.
// Cada Bounded Context tem sua propria linguagem ubiqua (Ubiquitous Language).
// O contexto de Order nao importa diretamente tipos de billing — a comunicacao
// ocorre via eventos de dominio ou por meio da Anti-Corruption Layer (ACL).
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    // TODO (exercício): adicionar suporte a PARTIALLY_REFUNDED no ACL
    // e definir qual estado do Order isso representa (ex: PARTIALLY_CANCELLED).
    PARTIALLY_REFUNDED
}

