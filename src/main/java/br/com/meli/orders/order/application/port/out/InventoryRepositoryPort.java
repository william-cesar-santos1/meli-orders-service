package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.InventoryItem;

import java.util.Optional;

public interface InventoryRepositoryPort {

    // PROBLEMA: sem lock, múltiplas transações podem ler o mesmo registro
    // simultaneamente, cada uma vê quantity = 1 e decrementa — overselling silencioso.
    Optional<InventoryItem> findByProductId(String productId);

    // SOLUCAO (Bloco 2 — pessimistic locking): SELECT FOR UPDATE garante que
    // apenas uma transação por vez leia e decremente o estoque.
    Optional<InventoryItem> findByProductIdWithLock(String productId);

    void save(InventoryItem item);
}

