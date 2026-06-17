package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.InventoryItem;

import java.util.Optional;

public interface FindInventoryPort {
    // SOLUCAO (Bloco 2 — pessimistic locking): SELECT FOR UPDATE garante que
    // apenas uma transação por vez leia e decremente o estoque.
    Optional<InventoryItem> findByProductIdWithLock(String productId);

}
