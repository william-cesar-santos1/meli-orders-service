package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;

// SOLUÇÃO: porta de saida dedicada para escrita do agregado Order.
// Cada porta tem uma responsabilidade unica (Interface Segregation Principle).
// O caso de uso depende apenas desta interface — nunca de implementacoes concretas.
// Principio: Dependency Inversion — dominio define a interface, infraestrutura implementa.
public interface SaveOrderPort {
    Order save(Order order);
    Order updateStatus(Long id, OrderStatus status);
}

