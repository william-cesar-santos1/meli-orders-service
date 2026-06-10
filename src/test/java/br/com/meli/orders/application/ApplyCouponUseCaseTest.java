package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// PROBLEMA: cada tipo de cupom é testado em isolamento com dados literais fixos.
// Nenhum cenário cobre a combinação de dois cupons no mesmo pedido —
// exatamente o caso que causou R$ 1,8 M de prejuízo em produção.
// O Pitest reporta MSI de ~40%: mutações no loop de descontos passam invisíveis.
@ExtendWith(MockitoExtension.class)
class ApplyCouponUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @InjectMocks
    private ApplyCouponUseCase useCase;

    private void givenOrderWithTotal(String total) {
        Order order = new Order(1L, "customer-test", List.of(), OrderStatus.CREATED,
            new BigDecimal(total), Instant.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    }

    @Test
    void shouldApplyCategoryDiscount() {
        givenOrderWithTotal("100.00");
        Money result = useCase.execute(1L, List.of(new CategoryCoupon("ELETRONICOS", new BigDecimal("0.10"))));
        assertThat(result).isEqualTo(new Money(new BigDecimal("90.00")));
    }

    @Test
    void shouldApplyMinValueDiscount() {
        givenOrderWithTotal("200.00");
        Money result = useCase.execute(1L, List.of(new MinValueCoupon(new BigDecimal("150.00"), new BigDecimal("20.00"))));
        assertThat(result).isEqualTo(new Money(new BigDecimal("180.00")));
    }

    @Test
    void shouldApplyPercentageCoupon() {
        givenOrderWithTotal("100.00");
        Money result = useCase.execute(1L, List.of(new PercentageCoupon(new BigDecimal("0.15"))));
        assertThat(result).isEqualTo(new Money(new BigDecimal("85.00")));
    }
}

