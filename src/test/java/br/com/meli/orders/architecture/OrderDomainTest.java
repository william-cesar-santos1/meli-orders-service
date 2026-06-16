package br.com.meli.orders.architecture;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.order.events.OrderPaid;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOLUÇÃO: testes de dominio para invariantes do agregado Order.
 * Sem dependencia de Spring ou infra — POJO puro. Rapidos e deterministas.
 * Principio: domain model testado de forma isolada, sem framework.
 */
@Tag("unit")
class OrderDomainTest {

    @Test
    void shouldCalculateTotalOnCreation() {
        List<OrderItem> items = List.of(
                new OrderItem("1", "prod-a", 2, new BigDecimal("50.00"), "Produto A"),
                new OrderItem("2", "prod-b", 1, new BigDecimal("30.00"), "Produto B")
        );

        Order order = Order.create("customer-1", items);

        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("130.00"));
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.customerId()).isEqualTo("customer-1");
    }

    @Test
    void shouldNotContainPaymentStatusField() {
        // SOLUÇÃO: valida que o agregado Order nao tem campo paymentStatus.
        // Garante que o contexto de billing nao vazou para o contexto de orders.
        var fields = Order.class.getDeclaredFields();
        for (var field : fields) {
            assertThat(field.getType().getName())
                    .as("Order nao deve ter campo do tipo PaymentStatus — viola Bounded Contexts")
                    .doesNotContain("PaymentStatus");
        }
    }

    @Test
    void shouldPublishOrderPaidEventWhenMarkedAsPaid() {
        // SOLUÇÃO: o agregado publica evento de dominio em vez de mutar estado de billing.
        Order order = new Order(1L, "customer-1", List.of(), OrderStatus.CREATED,
                new BigDecimal("100.00"), Instant.now());

        OrderPaid event = order.markAsPaid();

        assertThat(event).isNotNull();
        assertThat(event.orderId()).isEqualTo(1L);
        assertThat(event.customerId()).isEqualTo("customer-1");
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void shouldTransitionToCorrectStatus() {
        Order order = Order.create("customer-1", List.of(
                new OrderItem("1", "prod-a", 1, new BigDecimal("100.00"), "Produto A")
        ));

        Order paid = order.withStatus(OrderStatus.PAID);
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);

        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }
}

