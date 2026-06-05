package br.com.meli.orders.infrastructure.jpa;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderJpaAdapter implements OrderRepositoryPort {

    private final OrderRepository jpaRepository;

    public OrderJpaAdapter(OrderRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = OrderEntity.from(order);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Order updateStatus(Long id, OrderStatus status) {
        OrderEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        entity.setStatus(status.name());
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    // PROBLEMA: findByCustomerId usa LAZY loading para a associação 'items'.
    // O Hibernate emite 1 query adicional por pedido para carregar os itens —
    // o problema N+1. Com 100 pedidos: 101 queries ao banco por requisição.
    public List<Order> findByCustomerId(String customerId) {
        return jpaRepository.findByCustomerId(customerId).stream()
                .map(OrderEntity::toDomain)
                .toList();
    }
}

