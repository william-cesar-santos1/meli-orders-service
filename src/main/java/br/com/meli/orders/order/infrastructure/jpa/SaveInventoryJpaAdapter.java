package br.com.meli.orders.order.infrastructure.jpa;

import br.com.meli.orders.order.application.port.out.FindInventoryPort;
import br.com.meli.orders.order.application.port.out.SaveInventoryPort;
import br.com.meli.orders.order.domain.InventoryItem;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SaveInventoryJpaAdapter implements SaveInventoryPort, FindInventoryPort {

    private final InventoryRepository jpaRepository;

    public SaveInventoryJpaAdapter(InventoryRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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

