package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.ProductInfo;

public interface CatalogPort {
    ProductInfo getProduct(String productId);
}

