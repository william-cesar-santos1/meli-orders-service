package br.com.meli.order.infrastructure;

import br.com.meli.order.application.port.out.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Implementação do TransactionPort usando Spring @Transactional.
 * Fica na camada de infrastructure — o único lugar onde dependências de framework são aceitáveis.
 * Use cases injetam TransactionPort (interface de application), nunca este adapter diretamente.
 * Principio: Clean Architecture — framework coupling confinado à camada mais externa.
 */
@Component
public class SpringTransactionAdapter implements TransactionPort {

    @Override
    @Transactional
    public <T> T execute(Supplier<T> operation) {
        return operation.get();
    }
}

