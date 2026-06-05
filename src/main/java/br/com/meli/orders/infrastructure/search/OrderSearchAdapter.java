package br.com.meli.orders.infrastructure.search;

import br.com.meli.orders.application.port.out.OrderIndexPort;
import br.com.meli.orders.application.port.out.OrderSearchPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.infrastructure.jpa.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderSearchAdapter implements OrderSearchPort, OrderIndexPort {

    private final OrderRepository orderJpaRepository;
    private final OrderSearchRepository esRepository;

    public OrderSearchAdapter(OrderRepository orderJpaRepository,
                               OrderSearchRepository esRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.esRepository = esRepository;
    }

    @Override
    // PROBLEMA: LIKE '%q%' força um full table scan no PostgreSQL.
    // Sem índice de texto, cada busca percorre todos os registros da tabela.
    // Com 1 milhão de pedidos, a busca degrada linearmente.
    // Além disso, LIKE não faz stemming: 'tênis' não encontra 'tenis' ou 'Tênis'.
    public List<Order> search(String query) {
        return orderJpaRepository.findByProductDescriptionContaining(query).stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @Override
    // PROBLEMA: indexação direta no Elasticsearch sem transação distribuída.
    // Se o Elasticsearch estiver fora do ar, o pedido existe no PostgreSQL
    // mas não no índice de busca — inconsistência silenciosa.
    public void index(Order order) {
        esRepository.save(OrderSearchDocument.from(order));
    }
}

