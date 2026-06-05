package br.com.meli.orders.infrastructure.search;

import br.com.meli.orders.application.port.out.OrderSearchPort;
import br.com.meli.orders.domain.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderSearchAdapter implements OrderSearchPort {

    private final OrderSearchRepository esRepository;

    public OrderSearchAdapter(OrderSearchRepository esRepository) {
        this.esRepository = esRepository;
    }

    @Override
    // SOLUCAO (Bloco 3 — busca full-text): usa Elasticsearch com indice invertido.
    // Localiza documentos por termo em O(1), independente do volume total.
    // O analyzer 'portuguese' faz stemming: 'tenis', 'Tenis' e 'tenis' sao equivalentes.
    public List<Order> search(String query) {
        return esRepository.findByProductDescriptionContaining(query).stream()
                .map(OrderSearchDocument::toDomain)
                .toList();
    }
}
