package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.Order;

import java.util.List;

public interface OrderSearchPort {

    List<Order> search(String query);
}

