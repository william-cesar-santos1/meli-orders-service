package br.com.meli.orders.domain.exceptions;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(Object productId) {
        super("Produto indisponível: " + productId);
    }
}

