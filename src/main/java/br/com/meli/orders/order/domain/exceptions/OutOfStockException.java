package br.com.meli.orders.order.domain.exceptions;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(String productId) {
        super("Produto sem estoque: " + productId);
    }
}

