package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.InventoryItem;

import java.util.Optional;

public interface InventoryRepositoryPort {

    // PROBLEMA: sem lock, múltiplas transações podem ler o mesmo registro
    // simultaneamente, cada uma vê quantity = 1 e decrementa — overselling silencioso.
    Optional<InventoryItem> findByProductId(String productId);

    void save(InventoryItem item);
}

