package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.port.out.InventoryRepositoryPort;
import br.com.meli.orders.application.port.out.OrderEventPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.application.port.out.OutboxPort;
import br.com.meli.orders.domain.exceptions.OutOfStockException;
import br.com.meli.orders.infrastructure.jpa.InventoryEntity;
import br.com.meli.orders.infrastructure.jpa.InventoryRepository;
import br.com.meli.orders.infrastructure.search.OrderSearchRepository;
import org.junit.jupiter.api.*;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// SOLUÇÃO: o teste roda contra PostgreSQL 16 real em container descartável.
// O SELECT ... FOR UPDATE SKIP LOCKED que protege o estoque é exercitado pelo banco real.
// withReuse(true) elimina o cold-start de ~3s em execuções locais repetidas.
@SpringBootTest
@Testcontainers
@Tag("integration")
class CreateOrderUseCaseIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private CreateOrderUseCase createOrderUseCase;
    @Autowired private InventoryRepository inventoryRepository;
    // Elasticsearch não é necessário neste teste — mock evita ConnectionRefused ao subir contexto.
    @MockBean
    OrderSearchRepository orderSearchRepository;
    @MockBean
    OrderEventPort orderEventPort;

    @BeforeEach
    void seedInventory() {
        // insere produto com estoque = 1 para exercitar o lock concorrente
        inventoryRepository.save(new InventoryEntity("prod-limited", 1));
    }

    @Test
    void shouldCreateOrderAndReturnPersistedId() {
        // SOLUÇÃO: o id é gerado pela SEQUENCE real do PostgreSQL — nunca é null como no H2.
        inventoryRepository.save(new InventoryEntity("prod-tenis", 10));
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-alice",
            List.of(new CreateOrderRequest.Item("prod-tenis", 1, new BigDecimal("350.00"), "Tênis Nike"))
        );
        var order = createOrderUseCase.execute(request);
        assertThat(order.id()).isNotNull().isPositive();
    }

    @Test
    void shouldPreventDuplicateActiveOrderForSameCustomer() {
        // SOLUÇÃO: o SELECT ... FOR UPDATE SKIP LOCKED garante que apenas uma das duas
        // requisições concorrentes consome o estoque de qty=1.
        // Com H2, ambos os pedidos teriam sucesso — o bug de concorrência fica invisível no CI.
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-concurrent",
            List.of(new CreateOrderRequest.Item("prod-limited", 1, new BigDecimal("100.00"), "Produto Limitado"))
        );

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
            try { createOrderUseCase.execute(request); return true; }
            catch (OutOfStockException e) { return false; }
        });
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> {
            try { createOrderUseCase.execute(request); return true; }
            catch (OutOfStockException e) { return false; }
        });

        long successCount = Stream.of(first, second)
            .mapToLong(f -> { try { return f.get() ? 1L : 0L; } catch (Exception e) { return 0L; } })
            .sum();

        assertThat(successCount).isEqualTo(1);
    }
}

