package br.com.meli.order.infrastructure.outbox;

import br.com.meli.order.application.port.out.OutboxPort;
import br.com.meli.order.domain.order.Order;
import br.com.meli.order.infrastructure.search.OrderSearchDocument;
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

