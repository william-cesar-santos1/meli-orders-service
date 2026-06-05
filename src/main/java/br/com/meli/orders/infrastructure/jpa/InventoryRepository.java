package br.com.meli.orders.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {

    Optional<InventoryEntity> findByProductId(String productId);

    // PROBLEMA: sem @Lock, múltiplas transações podem ler o mesmo registro
    // de inventário simultaneamente. Cada uma lê quantity = 1, cada uma grava
    // quantity = 0. O resultado é quantity = 0 com múltiplos pedidos confirmados
    // para o mesmo item — overselling.
}

