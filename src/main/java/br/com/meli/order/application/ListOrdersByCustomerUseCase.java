package br.com.meli.order.application;

import br.com.meli.order.application.port.out.FindOrderPort;
import br.com.meli.order.domain.order.Order;

import java.util.List;

/**
 * Caso de uso: listar pedidos de um cliente.
 * POJO puro — sem anotações de framework.
 */
public class ListOrdersByCustomerUseCase {

    private final FindOrderPort findOrderPort;

    public ListOrdersByCustomerUseCase(FindOrderPort findOrderPort) {
        this.findOrderPort = findOrderPort;
    }

    public List<Order> execute(String customerId) {
        return findOrderPort.findByCustomerId(customerId);
    }
}
