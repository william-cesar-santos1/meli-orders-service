package br.com.meli.orders.order.application.saga;

import br.com.meli.orders.order.application.PlaceOrderCommand;
import br.com.meli.orders.order.application.port.out.OrderEventPort;
import br.com.meli.orders.order.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.OrderStatus;
import br.com.meli.orders.order.infrastructure.jpa.InventoryEntity;
import br.com.meli.orders.order.infrastructure.jpa.InventoryRepository;
import br.com.meli.orders.order.infrastructure.search.OrderSearchRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração end-to-end do fluxo de pagamento via Saga.
 * WireMock simula o billing service real (HTTP) — sem mocks de porta.
 * Valida o fluxo de compensação em cenários de sucesso e falha.
 *
 * Contexto de billing representado aqui apenas como adapter HTTP simulado,
 * conforme requisito: "O billing deve acontecer de forma simulada com o WireMock."
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class BillingServiceWireMockIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(true);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @MockBean
    OrderSearchRepository orderSearchRepository;

    @MockBean
    OrderEventPort orderEventPort;

    @DynamicPropertySource
    static void configureInfra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Aponta o billing service para o WireMock — porta dinâmica elimina conflito no CI
        registry.add("services.billing.url", wireMock::baseUrl);
        // Desabilita circuit breaker e retry para testes deterministas
        registry.add("resilience4j.circuitbreaker.instances.billing.sliding-window-size", () -> "100");
        registry.add("resilience4j.retry.instances.billing.max-attempts", () -> "1");
    }

    @Autowired private OrderSagaOrchestrator sagaOrchestrator;
    @Autowired private OrderRepositoryPort orderRepository;
    @Autowired private InventoryRepository inventoryRepository;

    @BeforeEach
    void setup() {
        inventoryRepository.save(new InventoryEntity("prod-billing-test", 100));
    }

    @Test
    void whenBillingReturnsCapture_orderShouldBePaid() {
        // WireMock simula billing service retornando CAPTURED (pagamento aprovado)
        wireMock.stubFor(post(urlEqualTo("/payments/charge"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"CAPTURED\"}")));

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-happy",
                List.of(new PlaceOrderCommand.Item("prod-billing-test", 1, new BigDecimal("150.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        // Compensação NÃO acionada — pedido confirmado como PAID
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        Order persisted = orderRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void whenBillingReturnsFailed_orderShouldBeCancelledViaCompensation() {
        // WireMock simula billing service retornando FAILED (pagamento recusado)
        wireMock.stubFor(post(urlEqualTo("/payments/charge"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"FAILED\"}")));

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-rejected",
                List.of(new PlaceOrderCommand.Item("prod-billing-test", 1, new BigDecimal("150.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        // Compensação acionada — pedido CANCELADO, sem pedido órfão
        assertThat(result.status())
                .as("Pedido deve ser CANCELLED quando billing recusa — Saga compensou corretamente")
                .isEqualTo(OrderStatus.CANCELLED);
        Order persisted = orderRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void whenBillingReturns503_orderShouldBeCancelledViaCompensation() {
        // WireMock simula billing service indisponível (503 Service Unavailable)
        wireMock.stubFor(post(urlEqualTo("/payments/charge"))
                .willReturn(aResponse().withStatus(503)));

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-503",
                List.of(new PlaceOrderCommand.Item("prod-billing-test", 1, new BigDecimal("50.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        // Compensação acionada via exceção HTTP — pedido CANCELADO
        assertThat(result.status())
                .as("Pedido deve ser CANCELLED quando billing retorna 503 — Saga compensou")
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void whenBillingTimesOut_orderShouldBeCancelledViaCompensation() {
        // WireMock simula timeout de rede (delay maior que read timeout de 2s)
        wireMock.stubFor(post(urlEqualTo("/payments/charge"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(3000)
                        .withBody("{\"status\": \"CAPTURED\"}")));

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-timeout",
                List.of(new PlaceOrderCommand.Item("prod-billing-test", 1, new BigDecimal("75.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        // Compensação acionada via timeout — pedido CANCELADO, não órfão
        assertThat(result.status())
                .as("Pedido deve ser CANCELLED quando billing sofre timeout — Saga compensou")
                .isEqualTo(OrderStatus.CANCELLED);
    }
}

