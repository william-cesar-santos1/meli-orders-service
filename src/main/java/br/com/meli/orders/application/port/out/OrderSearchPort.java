package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.Order;

import java.util.List;

public interface OrderSearchPort {

    List<Order> search(String query);
}

