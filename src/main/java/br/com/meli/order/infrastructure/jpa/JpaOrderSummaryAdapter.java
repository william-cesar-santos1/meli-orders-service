package br.com.meli.order.infrastructure.jpa;

import br.com.meli.order.application.port.out.FindOrderSummaryPort;
import br.com.meli.order.domain.order.OrderSummary;
import org.springframework.stereotype.Component;

import java.util.Optional;

// SOLUÇÃO: adapter JPA para leitura de resumo de pedido usando projecao JPQL.
// Retorna apenas os campos necessarios — evita carregar o agregado inteiro (com itens).
// Principio: Read Model / CQRS — o modelo de leitura e separado do agregado de escrita.
@Component
public class JpaOrderSummaryAdapter implements FindOrderSummaryPort {

    private final OrderSummaryRepository summaryRepository;

    public JpaOrderSummaryAdapter(OrderSummaryRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    @Override
    public Optional<OrderSummary> findSummaryById(Long orderId) {
        // SOLUÇÃO: projecao JPQL retorna apenas os campos necessarios —
        // evita carregar o agregado inteiro (todos os OrderItems).
        return summaryRepository.findSummaryById(orderId);
    }
}

