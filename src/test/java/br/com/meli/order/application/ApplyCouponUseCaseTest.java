package br.com.meli.order.application;

import br.com.meli.order.application.port.out.FindOrderPort;
import br.com.meli.order.domain.coupon.Money;
import br.com.meli.order.domain.order.Order;
import br.com.meli.order.domain.order.OrderStatus;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ApplyCouponUseCaseTest {

    @InjectMocks
    private ApplyCouponUseCase useCase;

    @TestFactory
    Stream<DynamicTest> discountCombinations() throws Exception {
        return CouponCombinationLoader.load("test-data/coupon-combinations.csv")
                .map(scenario -> dynamicTest(scenario.description(), () -> {
                    FindOrderPort findOrderPort = Mockito.mock(FindOrderPort.class);
                    ApplyCouponUseCase uc = new ApplyCouponUseCase(findOrderPort);
                    Order order = new Order(
                            1L, "customer-test", List.of(), OrderStatus.CREATED,
                            scenario.orderTotal().amount(), Instant.now());
                    when(findOrderPort.findById(1L)).thenReturn(Optional.of(order));
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
