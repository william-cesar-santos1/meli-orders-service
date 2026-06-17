package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.order.Order;

import java.util.List;

public interface OrderSearchPort {

    List<Order> search(String query);
}

