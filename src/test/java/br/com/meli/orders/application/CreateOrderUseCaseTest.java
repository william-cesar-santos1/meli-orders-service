package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// PROBLEMA: H2 em memória não reproduz o comportamento de lock do PostgreSQL real.
// O SELECT ... FOR UPDATE SKIP LOCKED que protege o estoque é ignorado pelo H2.
// Requisições concorrentes para o mesmo produto passam no CI e esgotam estoque em produção.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CreateOrderUseCaseTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void shouldCreateOrder() {
        // PROBLEMA: o id pode ser null porque o SEQUENCE do PostgreSQL não é reproduzido pelo H2.
        // O teste passa mas não verifica o comportamento real de persistência.
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-alice",
            List.of(new CreateOrderRequest.Item("prod-tenis", 1, new BigDecimal("350.00"), "Tênis Nike"))
        );
        var order = createOrderUseCase.execute(request);
        assertThat(order).isNotNull();
    }
}

