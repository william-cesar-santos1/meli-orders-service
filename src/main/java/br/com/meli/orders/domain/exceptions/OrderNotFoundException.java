package br.com.meli.orders.domain.exceptions;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Pedido não encontrado: " + id);
    }

    public OrderNotFoundException(String id) {
        super("Pedido não encontrado: " + id);
    }
}

