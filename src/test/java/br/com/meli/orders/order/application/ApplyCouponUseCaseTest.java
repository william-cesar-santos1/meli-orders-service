package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.OrderRepositoryPort;

import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.OrderStatus;
import br.com.meli.orders.shared.domain.Coupon;
import br.com.meli.orders.shared.domain.Money;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ApplyCouponUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @InjectMocks
    private ApplyCouponUseCase useCase;

    @TestFactory
    Stream<DynamicTest> discountCombinations() throws Exception {
        return CouponCombinationLoader.load("test-data/coupon-combinations.csv")
            .map(scenario -> dynamicTest(scenario.description(), () -> {
                OrderRepositoryPort repo = Mockito.mock(OrderRepositoryPort.class);
                ApplyCouponUseCase uc = new ApplyCouponUseCase(repo);
                Order order = new Order(
                    1L, "customer-test", List.of(), OrderStatus.CREATED,
                    scenario.orderTotal().amount(), Instant.now());
                when(repo.findById(1L)).thenReturn(Optional.of(order));
                Money result = uc.execute(1L, scenario.coupons());
                assertThat(result).isEqualTo(scenario.expectedTotal());
            }));
    }

    @Test
    @Tag("unit")
    void shouldRejectNullCouponList() {
        assertTimeoutPreemptively(Duration.ofMillis(100), () ->
            assertThatThrownBy(() -> useCase.execute(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
        );
    }
}
