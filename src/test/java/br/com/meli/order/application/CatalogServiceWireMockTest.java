package br.com.meli.order.application;

import br.com.meli.order.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.order.domain.exceptions.ProductUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        registry.add("services.catalog.url", wireMock::baseUrl);
    }

    @Autowired
    private AddItemToOrderUseCase addItemToOrderUseCase;

    @Test
    void shouldRejectUnavailableProduct() {
        wireMock.stubFor(get(urlPathMatching("/products/.*"))
            .willReturn(aResponse().withStatus(422)
                .withBody("{\"available\": false}")));

        assertThatThrownBy(() -> addItemToOrderUseCase.execute(1L, "prod-test", 1))
            .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void shouldHandleCatalogServiceUnavailable() {
        wireMock.stubFor(get(urlPathMatching("/products/.*"))
            .willReturn(aResponse().withStatus(503).withFixedDelay(3000)));

        assertThatThrownBy(() -> addItemToOrderUseCase.execute(1L, "prod-test", 1))
            .isInstanceOf(CatalogServiceUnavailableException.class);
    }
}
