package br.com.meli.orders.application;

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
 * PROBLEMA: este teste demonstra o cenario de pedido orfao.
 * Quando o BillingClient falha apos o pedido ser criado, o pedido permanece
 * no banco com status CREATED sem compensacao. Este teste FALHA neste branch
 * porque a implementacao nao tem mecanismo de compensacao — o comportamento
 * esperado (CANCELLED) nao acontece. Sera corrigido em feature/saga-resilience-9.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class PlaceOrderAndChargeUseCaseIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(true);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private PlaceOrderAndChargeUseCase placeOrderAndChargeUseCase;
    @Autowired private OrderRepositoryPort orderRepository;
    @Autowired private InventoryRepository inventoryRepository;

    @MockBean OrderSearchRepository orderSearchRepository;
    @MockBean OrderEventPort orderEventPort;
    @MockBean BillingClient billingClient;

    @BeforeEach
    void setup() {
        inventoryRepository.save(new InventoryEntity("prod-orphan-test", 10));
    }

    @Test
    void whenBillingFails_orderShouldBeCancelledNotOrphaned() {
        // Simula falha do servico de billing
        when(billingClient.charge(any(), any())).thenReturn(PaymentStatus.FAILED);

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-orphan",
                List.of(new CreateOrderRequest.Item("prod-orphan-test", 1, new BigDecimal("100.00"), "Produto Teste"))
        );

        Order result = placeOrderAndChargeUseCase.execute(request);

        // PROBLEMA: sem compensacao, o pedido fica com status CREATED (nao CANCELLED)
        // quando o billing falha. Este assert FALHA intencionalmente neste branch
        // para demonstrar o problema de inconsistencia em fluxos distribuidos sem Saga.
        // Comportamento esperado: pedido cancelado quando billing falha.
        // Comportamento atual: pedido orfao com status CREATED.
        Order persisted = orderRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.status())
                .as("Pedido deveria ser CANCELLED quando billing falha, mas esta ORPHANED com status: %s", persisted.status())
                .isEqualTo(OrderStatus.CANCELLED);
    }
}

