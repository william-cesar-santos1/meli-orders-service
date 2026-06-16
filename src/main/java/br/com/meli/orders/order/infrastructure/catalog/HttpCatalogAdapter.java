package br.com.meli.orders.order.infrastructure.catalog;

import br.com.meli.orders.order.application.port.out.CatalogPort;
import br.com.meli.orders.order.domain.ProductInfo;
import br.com.meli.orders.order.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.orders.order.domain.exceptions.ProductUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpCatalogAdapter implements CatalogPort {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String catalogBaseUrl;

    public HttpCatalogAdapter(@Value("${services.catalog.url}") String catalogBaseUrl) {
        this.catalogBaseUrl = catalogBaseUrl;
    }

    @Override
    public ProductInfo getProduct(String productId) {
        String url = catalogBaseUrl + "/products/" + productId;
        try {
            return restTemplate.getForObject(url, ProductInfo.class);
        } catch (HttpClientErrorException e) {
            // 4xx: produto indisponível ou não encontrado no catálogo
            throw new ProductUnavailableException(productId);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 5xx ou timeout: serviço de catálogo instável
            throw new CatalogServiceUnavailableException(productId);
        }
    }
}

