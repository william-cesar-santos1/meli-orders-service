package br.com.meli.orders.application;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import br.com.meli.orders.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.orders.domain.exceptions.ProductUnavailableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// SOLUÇÃO: WireMock simula o serviço de catálogo com cenários de falha offline.
// O teste não depende do serviço real estar no ar — reproduzível em qualquer ambiente de CI.
@SpringBootTest
@Testcontainers
@Tag("integration")
class CatalogServiceWireMockTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @DynamicPropertySource
    static void configureInfra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // SOLUÇÃO: aponta o catálogo para o WireMock — porta dinâmica elimina conflito no CI.
        registry.add("services.catalog.url", wireMock::baseUrl);
    }

    @Autowired
    private AddItemToOrderUseCase addItemToOrderUseCase;

    @Test
    void shouldRejectUnavailableProduct() {
        // SOLUÇÃO: WireMock retorna 422 — o use case lança ProductUnavailableException.
        // Verifica o tipo exato da exceção, não apenas que "alguma coisa" foi lançada.
        wireMock.stubFor(get(urlPathMatching("/products/.*"))
            .willReturn(aResponse().withStatus(422)
                .withBody("{\"available\": false}")));

        assertThatThrownBy(() -> addItemToOrderUseCase.execute(1L, "prod-test", 1))
            .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void shouldHandleCatalogServiceUnavailable() {
        // SOLUÇÃO: simula instabilidade do serviço externo com 503 + delay de 3s.
        // Verifica que o use case lança CatalogServiceUnavailableException
        // em vez de travar a thread indefinidamente.
        wireMock.stubFor(get(urlPathMatching("/products/.*"))
            .willReturn(aResponse().withStatus(503).withFixedDelay(3000)));

        assertThatThrownBy(() -> addItemToOrderUseCase.execute(1L, "prod-test", 1))
            .isInstanceOf(CatalogServiceUnavailableException.class);
    }
}

