package br.com.meli.orders.infrastructure.outbox;

import br.com.meli.orders.application.port.out.OutboxPort;
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
    public void save(String aggregateId, String eventType, String payload) {
        OutboxEntry entry = new OutboxEntry(
                UUID.randomUUID(),
                aggregateId,
                eventType,
                payload,
                Instant.now(),
                null
        );
        repository.save(entry);
    }
}

