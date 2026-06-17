package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.InventoryItem;

public interface SaveInventoryPort {

    void save(InventoryItem item);
}

