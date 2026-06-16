package br.com.meli.orders.application.saga;

import br.com.meli.orders.api.BillingClient;
import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.port.out.OrderEventPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.billing.PaymentStatus;
import br.com.meli.orders.infrastructure.jpa.InventoryEntity;
import br.com.meli.orders.infrastructure.jpa.InventoryRepository;
import br.com.meli.orders.infrastructure.search.OrderSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SOLUÇÃO: testes de integracao do fluxo distribuido via Saga Orchestrator.
 * Valida cenario feliz (pagamento confirmado) e cenario compensado (billing falha).
 * Principio: Saga Pattern garante consistencia mesmo com falhas parciais.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class OrderSagaOrchestratorIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(true);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private OrderSagaOrchestrator sagaOrchestrator;
    @Autowired private OrderRepositoryPort orderRepository;
    @Autowired private InventoryRepository inventoryRepository;

    @MockBean OrderSearchRepository orderSearchRepository;
    @MockBean OrderEventPort orderEventPort;
    @MockBean BillingClient billingClient;

    @BeforeEach
    void setup() {
        inventoryRepository.save(new InventoryEntity("prod-saga-test", 10));
    }

    @Test
    void whenPaymentSucceeds_orderShouldBePaid() {
        // SOLUÇÃO: cenario feliz — billing confirma pagamento
        when(billingClient.charge(any(), any())).thenReturn(PaymentStatus.CAPTURED);

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-happy",
                List.of(new CreateOrderRequest.Item("prod-saga-test", 1, new BigDecimal("100.00"), "Produto")));

        Order result = sagaOrchestrator.execute(request);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        Order persisted = orderRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void whenBillingFails_orderShouldBeCancelledNotOrphaned() {
        // SOLUÇÃO: cenario compensado — billing falha, saga cancela o pedido automaticamente.
        // Este teste PASSA neste branch (ao contrario do PlaceOrderAndChargeUseCaseIT
        // que falha em feature/ms-base sem saga).
        when(billingClient.charge(any(), any())).thenReturn(PaymentStatus.FAILED);

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-sad",
                List.of(new CreateOrderRequest.Item("prod-saga-test", 1, new BigDecimal("100.00"), "Produto")));

        Order result = sagaOrchestrator.execute(request);

        // SOLUÇÃO: pedido CANCELADO — sem pedido orfao!
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        Order persisted = orderRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.status())
                .as("Pedido deve ser CANCELLED quando billing falha — Saga compensou corretamente")
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void whenBillingThrowsException_orderShouldBeCancelled() {
        // SOLUÇÃO: excecao (ex: timeout, circuit aberto) tambem aciona compensacao.
        when(billingClient.charge(any(), any())).thenThrow(new RuntimeException("Billing indisponivel"));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-exception",
                List.of(new CreateOrderRequest.Item("prod-saga-test", 1, new BigDecimal("50.00"), "Produto")));

        Order result = sagaOrchestrator.execute(request);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    }
}

