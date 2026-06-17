package br.com.meli.order.application;

import br.com.meli.order.domain.exceptions.OutOfStockException;
import br.com.meli.order.infrastructure.jpa.InventoryEntity;
import br.com.meli.order.infrastructure.jpa.InventoryRepository;
import br.com.meli.order.infrastructure.search.OrderSearchRepository;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private InventoryRepository inventoryRepository;
    @MockBean
    OrderSearchRepository orderSearchRepository;

    @BeforeEach
    void seedInventory() {
        inventoryRepository.save(new InventoryEntity("prod-limited", 1));
    }

    @Test
    void shouldCreateOrderAndReturnPersistedId() {
        inventoryRepository.save(new InventoryEntity("prod-tenis", 10));
        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-alice",
                List.of(new PlaceOrderCommand.Item("prod-tenis", 1, new BigDecimal("350.00"), "Tênis Nike"))
        );
        var order = createOrderUseCase.execute(command);
        assertThat(order.id()).isNotNull().isPositive();
    }

    @Test
    void shouldPreventDuplicateActiveOrderForSameCustomer() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "customer-concurrent",
                List.of(new PlaceOrderCommand.Item("prod-limited", 1, new BigDecimal("100.00"), "Produto Limitado"))
        );

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
            try {
                createOrderUseCase.execute(command);
                return true;
            } catch (OutOfStockException e) {
                return false;
            }
        });
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> {
            try {
                createOrderUseCase.execute(command);
                return true;
            } catch (OutOfStockException e) {
                return false;
            }
        });

        long successCount = Stream.of(first, second)
                .mapToLong(f -> {
                    try {
                        return f.get() ? 1L : 0L;
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .sum();

        assertThat(successCount).isEqualTo(1);
    }
}
