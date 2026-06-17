package br.com.meli.architecture;

import br.com.meli.order.application.port.out.FindOrderSummaryPort;
import br.com.meli.order.domain.order.OrderSummary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class FindOrderSummaryPortFakeTest {

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
        assertThat(fakePort.findSummaryById(99L)).isEmpty();
    }

    @Test
    void portInterfaceShouldNotDependOnSpringOrInfrastructure() {
        var methods = FindOrderSummaryPort.class.getMethods();
        assertThat(methods).isNotEmpty();
        FakeOrderSummaryPort fake = new FakeOrderSummaryPort();
        assertThat(fake).isInstanceOf(FindOrderSummaryPort.class);
    }
}
