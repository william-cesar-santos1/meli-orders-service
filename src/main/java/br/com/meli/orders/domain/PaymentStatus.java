package br.com.meli.orders.domain;

// PROBLEMA: PaymentStatus pertence ao contexto de Billing, mas foi definido
// dentro do pacote de domain/order. Isso cria um vazamento de contexto (context leaking):
// o agregado Order passa a carregar conceitos que nao sao de sua responsabilidade.
// Violacao do principio de Bounded Contexts (DDD) — cada contexto deve ter
// seu proprio modelo linguistico e nao deve depender da linguagem de outro contexto.
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED
}

