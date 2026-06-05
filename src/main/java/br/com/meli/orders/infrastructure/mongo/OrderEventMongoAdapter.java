package br.com.meli.orders.infrastructure.mongo;

import br.com.meli.orders.application.port.out.OrderEventPort;
import br.com.meli.orders.domain.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderEventMongoAdapter implements OrderEventPort {

    private final OrderEventMongoRepository repository;

    public OrderEventMongoAdapter(OrderEventMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordOrderPlaced(Order order) {
        OrderEventDocument event = new OrderEventDocument();
        event.setId(UUID.randomUUID().toString());
        event.setOrderId(order.id() != null ? order.id().toString() : null);
        event.setCustomerId(order.customerId());
        event.setEventType("OrderPlaced");
        event.setOccurredAt(Instant.now());
        event.setPayload(Map.of(
                "orderId", order.id() != null ? order.id().toString() : "",
                "customerId", order.customerId(),
                "status", order.status().name(),
                "totalAmount", order.totalAmount().toString()
        ));
        repository.save(event);
    }
}

