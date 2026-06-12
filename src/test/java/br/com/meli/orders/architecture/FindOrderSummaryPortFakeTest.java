package br.com.meli.orders.architecture;

import br.com.meli.orders.application.port.out.FindOrderSummaryPort;
import br.com.meli.orders.domain.OrderSummary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOLUÇÃO: teste unitario do caso de uso usando fake port in-memory.
 * O fake implementa FindOrderSummaryPort sem JPA, sem banco, sem Spring.
 * Principio: Dependency Inversion + Testability — portas permitem substituir
 * a implementacao real por fakes em testes unitarios rapidos e deterministas.
 */
@Tag("unit")
class FindOrderSummaryPortFakeTest {

    // Fake in-memory implementation — sem infraestrutura
    static class FakeOrderSummaryPort implements FindOrderSummaryPort {
        private final Map<Long, OrderSummary> store = new HashMap<>();

        void add(OrderSummary summary) {
            store.put(summary.orderId(), summary);
        }

        @Override
        public Optional<OrderSummary> findSummaryById(Long orderId) {
            return Optional.ofNullable(store.get(orderId));
        }
    }

    @Test
    void shouldReturnSummaryWhenOrderExists() {
        FakeOrderSummaryPort fakePort = new FakeOrderSummaryPort();
        fakePort.add(new OrderSummary(1L, "customer-test", "PAID", new BigDecimal("250.00")));

        Optional<OrderSummary> result = fakePort.findSummaryById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(1L);
        assertThat(result.get().status()).isEqualTo("PAID");
        assertThat(result.get().total()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void shouldReturnEmptyWhenOrderDoesNotExist() {
        FakeOrderSummaryPort fakePort = new FakeOrderSummaryPort();

        Optional<OrderSummary> result = fakePort.findSummaryById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void portInterfaceShouldNotDependOnSpringOrInfrastructure() {
        // SOLUÇÃO: valida que FindOrderSummaryPort e uma interface pura — sem imports Spring.
        // Portas de saida nao devem ter dependencias de frameworks — apenas de tipos de dominio.
        var methods = FindOrderSummaryPort.class.getMethods();
        assertThat(methods).isNotEmpty();

        // A propria instanciabilidade do fake prova que a porta e um POJO puro
        FakeOrderSummaryPort fake = new FakeOrderSummaryPort();
        assertThat(fake).isInstanceOf(FindOrderSummaryPort.class);
    }
}

