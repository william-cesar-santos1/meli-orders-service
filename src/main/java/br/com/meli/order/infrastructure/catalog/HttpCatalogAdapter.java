package br.com.meli.order.infrastructure.catalog;

import br.com.meli.order.application.port.out.CatalogPort;
import br.com.meli.order.domain.ProductInfo;
import br.com.meli.order.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.order.domain.exceptions.ProductUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * SOLUCAO (Rastreabilidade): usa RestTemplateBuilder injetado pelo Spring Boot,
 * que automaticamente adiciona o ObservationClientHttpRequestInterceptor.
 * Isso faz com que cada chamada HTTP ao servico de Catalog gere um span filho
 * no trace atual, visivel no Jaeger com URL, status HTTP e duracao.
 *
 * ANTES: new RestTemplate() — sem interceptor de tracing, chamadas invisiveis no Jaeger.
 * DEPOIS: builder.build() — span criado automaticamente para cada chamada HTTP de saida.
 */
@Component
public class HttpCatalogAdapter implements CatalogPort {

    // SOLUCAO: RestTemplate construido via builder para herdar o interceptor de observabilidade.
    private final RestTemplate restTemplate;
    private final String catalogBaseUrl;

    public HttpCatalogAdapter(RestTemplateBuilder builder,
                              @Value("${services.catalog.url}") String catalogBaseUrl) {
        this.restTemplate = builder.build();
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

