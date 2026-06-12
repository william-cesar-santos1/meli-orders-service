package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.*;
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

// SOLUÇÃO: @TestFactory gera um caso de teste por linha do CSV — incluindo combinações duplas.
// Novos cenários de negócio entram no CSV sem tocar no código Java de teste.
// Cada caso falha de forma isolada no relatório do JUnit, facilitando o diagnóstico.
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
                // mock local por cenário — evita contaminação de estado entre dynamic tests
                OrderRepositoryPort repo = Mockito.mock(OrderRepositoryPort.class);
                ApplyCouponUseCase uc = new ApplyCouponUseCase(repo);
                // import explícito para evitar ambiguidade com org.junit.jupiter.api.Order
                br.com.meli.orders.domain.Order order = new br.com.meli.orders.domain.Order(
                    1L, "customer-test", List.of(), OrderStatus.CREATED,
                    scenario.orderTotal().amount(), Instant.now(), br.com.meli.orders.domain.PaymentStatus.PENDING);
                when(repo.findById(1L)).thenReturn(Optional.of(order));
                Money result = uc.execute(1L, scenario.coupons());
                assertThat(result).isEqualTo(scenario.expectedTotal());
            }));
    }

    @Test
    @Tag("unit")
    void shouldRejectNullCouponList() {
        // SOLUÇÃO: assertTimeoutPreemptively garante que o cálculo não excede 100ms.
        // Detecta regressões de desempenho silenciosas antes que cheguem ao load test.
        // A validação de null ocorre antes do acesso ao repositório — nenhum stub necessário.
        assertTimeoutPreemptively(Duration.ofMillis(100), () ->
            assertThatThrownBy(() -> useCase.execute(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
        );
    }
}
