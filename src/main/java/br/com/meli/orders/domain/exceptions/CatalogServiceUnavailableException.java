package br.com.meli.orders.domain.exceptions;

public class CatalogServiceUnavailableException extends RuntimeException {
    public CatalogServiceUnavailableException(Object productId) {
        super("Serviço de catálogo indisponível ao buscar produto: " + productId);
    }
}

