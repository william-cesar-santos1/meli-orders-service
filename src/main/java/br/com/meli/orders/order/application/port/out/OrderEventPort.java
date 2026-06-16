package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.Order;

public interface OrderEventPort {

    // SOLUCAO (Bloco 3 — MongoDB / log de eventos): registra o evento de dominio
    // para auditoria e replay. Consistencia eventual e aceitavel aqui —
    // nao e fonte de verdade transacional.
    void recordOrderPlaced(Order order);
}

