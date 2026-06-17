package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.InventoryItem;

import java.util.Optional;

public interface SaveInventoryPort {

    void save(InventoryItem item);
}

