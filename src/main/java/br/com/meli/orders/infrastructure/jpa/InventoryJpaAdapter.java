package br.com.meli.orders.infrastructure.jpa;

import br.com.meli.orders.application.port.out.InventoryRepositoryPort;
import br.com.meli.orders.domain.InventoryItem;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InventoryJpaAdapter implements InventoryRepositoryPort {

    private final InventoryRepository jpaRepository;

    public InventoryJpaAdapter(InventoryRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    // PROBLEMA: sem lock, múltiplas transações podem ler o mesmo registro simultaneamente.
    // Cada uma lê quantity = 1, cada uma grava quantity = 0 — overselling silencioso.
    public Optional<InventoryItem> findByProductId(String productId) {
        return jpaRepository.findByProductId(productId)
                .map(e -> new InventoryItem(e.getProductId(), e.getName(), e.getQuantity()));
    }

    @Override
    // SOLUCAO (Bloco 2 — pessimistic locking): delega para SELECT FOR UPDATE,
    // bloqueando o registro até o commit/rollback da transação corrente.
    public Optional<InventoryItem> findByProductIdWithLock(String productId) {
        return jpaRepository.findByProductIdWithLock(productId)
                .map(e -> new InventoryItem(e.getProductId(), e.getName(), e.getQuantity()));
    }

    @Override
    public void save(InventoryItem item) {
        InventoryEntity entity = jpaRepository.findById(item.productId())
                .orElseThrow(() -> new IllegalStateException("Inventory not found: " + item.productId()));
        entity.setQuantity(item.quantity());
        jpaRepository.save(entity);
    }
}

