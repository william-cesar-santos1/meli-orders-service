package br.com.meli.orders.infrastructure.jpa;

import br.com.meli.orders.application.port.out.FindOrderPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.application.port.out.SaveOrderPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// SOLUÇÃO: OrderJpaAdapter implementa os novos ports especificos (SaveOrderPort, FindOrderPort)
// alem do OrderRepositoryPort existente (para compatibilidade).
// Spring injeta este adapter em qualquer caso de uso que declare SaveOrderPort ou FindOrderPort.
// Principio: Adapter Pattern + Dependency Inversion.
@Component
public class OrderJpaAdapter implements OrderRepositoryPort, SaveOrderPort, FindOrderPort {

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
    // SOLUÇÃO: usa JOIN FETCH para carregar pedido + itens em uma unica query SQL.
    public List<Order> findByCustomerId(String customerId) {
        return jpaRepository.findWithItemsByCustomer(customerId).stream()
                .map(OrderEntity::toDomain)
                .toList();
    }
}
