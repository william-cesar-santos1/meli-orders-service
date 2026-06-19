package br.com.meli.order.application.saga;

import br.com.meli.order.application.PlaceOrderCommand;
import br.com.meli.order.application.acl.PaymentResult;
import br.com.meli.order.application.port.out.BillingPort;
import br.com.meli.order.application.port.out.FindOrderPort;
import br.com.meli.order.domain.order.Order;
import br.com.meli.order.domain.order.OrderStatus;
import br.com.meli.order.infrastructure.jpa.InventoryEntity;
import br.com.meli.order.infrastructure.jpa.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes de integração do fluxo Saga Orchestrator.
 * Usa @MockBean BillingPort para focar na lógica da saga (compensação).
 * Para teste end-to-end com HTTP real, veja BillingServiceWireMockIT.
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

    @Autowired
    private OrderSagaOrchestrator sagaOrchestrator;
    @Autowired
    private FindOrderPort findOrderPort;
    @Autowired
    private InventoryRepository inventoryRepository;

    @MockBean
    BillingPort billingPort;

    @BeforeEach
    void setup() {
        inventoryRepository.save(new InventoryEntity("prod-saga-test", 10));
    }

    @Test
    @Transactional
    void whenPaymentSucceeds_orderShouldBePaid() {
        when(billingPort.charge(any(), any())).thenReturn(PaymentResult.CAPTURED);

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-happy",
                List.of(new PlaceOrderCommand.Item("prod-saga-test", 1, new BigDecimal("100.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        Order persisted = findOrderPort.findById(result.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @Transactional
    void whenBillingFails_orderShouldBeCancelledNotOrphaned() {
        when(billingPort.charge(any(), any())).thenReturn(PaymentResult.FAILED);

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-sad",
                List.of(new PlaceOrderCommand.Item("prod-saga-test", 1, new BigDecimal("100.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        Order persisted = findOrderPort.findById(result.id()).orElseThrow();
        assertThat(persisted.status())
                .as("Pedido deve ser CANCELLED quando billing falha — Saga compensou corretamente")
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void whenBillingThrowsException_orderShouldBeCancelled() {
        when(billingPort.charge(any(), any())).thenThrow(new RuntimeException("Billing indisponível"));

        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-exception",
                List.of(new PlaceOrderCommand.Item("prod-saga-test", 1, new BigDecimal("50.00"), "Produto")));

        Order result = sagaOrchestrator.execute(command);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
