package br.com.meli.order.infrastructure.outbox;

import br.com.meli.order.infrastructure.search.OrderSearchDocument;
import br.com.meli.order.infrastructure.search.OrderSearchRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxProcessor {

    private final OutboxJpaRepository outboxRepository;
    private final OrderSearchRepository searchRepository;

    public OutboxProcessor(OutboxJpaRepository outboxRepository,
                           OrderSearchRepository searchRepository) {
        this.outboxRepository = outboxRepository;
        this.searchRepository = searchRepository;
    }

    // SOLUCAO (Bloco 3 — Outbox processor com controle de concorrencia):
    // findUnprocessedForUpdate usa FOR UPDATE SKIP LOCKED — cada entrada
    // e processada por exatamente um pod, mesmo com multiplas instancias ativas.
    // Se o Elasticsearch falhar, a transacao faz rollback: o lock e liberado
    // e a entrada permanece pendente para o proximo ciclo.
    // A entrada so e marcada como processada apos a indexacao confirmar E
    // a transacao confirmar no Postgres — sem perda de evento.
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void process() {
        List<OutboxEntry> pending = outboxRepository.findUnprocessedForUpdate(10);
        for (OutboxEntry entry : pending) {
            try {
                searchRepository.save(OrderSearchDocument.fromJson(entry.getPayload()));
                entry.setProcessedAt(Instant.now());
                outboxRepository.save(entry);
            } catch (Exception e) {
                // rollback: lock liberado, entrada volta para a fila
                // producao: adicionar retry_count e dead-letter apos N falhas
                throw new RuntimeException("Falha ao processar outbox entry " + entry.getId(), e);
            }
        }
    }
}

