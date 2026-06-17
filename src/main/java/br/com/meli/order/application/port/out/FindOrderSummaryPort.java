package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.order.OrderSummary;

import java.util.Optional;

// SOLUÇÃO: porta dedicada para leitura de resumo de pedido — segregada de FindOrderPort
// para suportar projecoes otimizadas sem expor o agregado completo.
// Um controller de consulta que so precisa de status e total nao deve
// carregar todos os OrderItems do banco — isso e desperdicador de recursos.
// Principio: Interface Segregation + Read Model Optimization.
public interface FindOrderSummaryPort {
    Optional<OrderSummary> findSummaryById(Long orderId);
}

