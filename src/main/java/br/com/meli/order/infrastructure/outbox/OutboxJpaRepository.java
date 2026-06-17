package br.com.meli.order.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntry, UUID> {

    // SOLUCAO (Bloco 3 — concorrencia entre pods): FOR UPDATE SKIP LOCKED e nativo
    // do PostgreSQL. Pod A processa entradas 1-10; Pod B pula essas (SKIP LOCKED)
    // e avanca para 11-20. Paralelismo real sem duplicacao nem deadlock.
    @Query(value = """
            SELECT * FROM order_outbox
            WHERE processed_at IS NULL
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntry> findUnprocessedForUpdate(@Param("limit") int limit);
}

