package br.com.meli.order.application.acl;

/**
 * Tipo ACL (Anti-Corruption Layer) que representa o resultado de uma cobrança
 * do ponto de vista do contexto de Order.
 *
 * Isola o domínio de Order da linguagem e do modelo de dados do serviço externo de Billing.
 * O BillingHttpAdapter (infrastructure) é responsável por mapear a resposta HTTP do billing
 * para este tipo — sem que o domínio ou os casos de uso conheçam o contrato HTTP externo.
 *
 * Princípio: ACL (DDD) — previne que modelos de contextos externos "contaminem" o modelo interno.
 */
public enum PaymentResult {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}

