package br.com.meli.order.application.port.out;

import java.util.function.Supplier;

/**
 * Porta de saída para controle transacional.
 * Permite que use cases declarem limite de transação sem depender do Spring Framework.
 * A implementação concreta (SpringTransactionAdapter) usa @Transactional do Spring,
 * mantendo a camada de application livre de dependências de framework.
 *
 * Principio: Dependency Inversion — application define o contrato,
 * infrastructure fornece a implementação transacional concreta.
 */
public interface TransactionPort {
    <T> T execute(Supplier<T> operation);
}

