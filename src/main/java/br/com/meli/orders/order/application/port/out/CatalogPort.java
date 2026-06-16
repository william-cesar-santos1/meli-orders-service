package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.ProductInfo;

public interface CatalogPort {
    ProductInfo getProduct(String productId);
}

