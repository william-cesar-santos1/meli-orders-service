package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.ProductInfo;

public interface CatalogPort {
    ProductInfo getProduct(String productId);
}

