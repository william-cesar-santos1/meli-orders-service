package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.Order;

import java.util.List;
import java.util.Optional;

// SOLUÇÃO: porta de saida dedicada para leitura do agregado Order.
// Separar leitura de escrita (Command Query Separation) permite otimizacoes
// independentes: a leitura pode usar projecoes, caches ou read replicas
// sem impactar o fluxo de escrita.
public interface FindOrderPort {
    Optional<Order> findById(Long id);
    List<Order> findByCustomerId(String customerId);
}

