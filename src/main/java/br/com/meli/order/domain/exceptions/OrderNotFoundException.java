package br.com.meli.order.domain.exceptions;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Pedido não encontrado: " + id);
    }

    public OrderNotFoundException(String id) {
        super("Pedido não encontrado: " + id);
    }
}

