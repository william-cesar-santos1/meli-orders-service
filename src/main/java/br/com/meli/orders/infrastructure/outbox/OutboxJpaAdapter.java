package br.com.meli.orders.infrastructure.outbox;

import br.com.meli.orders.application.port.out.OutboxPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.infrastructure.search.OrderSearchDocument;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxJpaAdapter implements OutboxPort {

    private final OutboxJpaRepository repository;

    public OutboxJpaAdapter(OutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(String aggregateId, String eventType, Order order) {
        OutboxEntry entry = new OutboxEntry(
                UUID.randomUUID(),
                aggregateId,
                eventType,
                OrderSearchDocument.from(order).toJson(),
                Instant.now(),
                null
        );
        repository.save(entry);
    }
}

