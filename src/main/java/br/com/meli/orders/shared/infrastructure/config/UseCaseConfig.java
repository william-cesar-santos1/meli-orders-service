package br.com.meli.orders.shared.infrastructure.config;

import br.com.meli.orders.billing.application.acl.BillingPaymentTranslator;
import br.com.meli.orders.billing.application.port.out.BillingPort;
import br.com.meli.orders.order.application.*;
import br.com.meli.orders.order.application.port.out.*;
import br.com.meli.orders.order.application.saga.OrderSagaOrchestrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração central: instancia todos os casos de uso como @Bean Spring.
 * Os casos de uso são POJOs puros — sem @Service, sem @Transactional, sem dependências de framework.
 * Principio: Dependency Rule (Clean Architecture) — frameworks ficam na camada mais externa.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public BillingPaymentTranslator billingPaymentTranslator() {
        return new BillingPaymentTranslator();
    }

    @Bean
    public CreateOrderUseCase createOrderUseCase(
            OrderRepositoryPort orderRepository,
            InventoryRepositoryPort inventoryRepository,
            OutboxPort outboxPort,
            OrderEventPort orderEventPort,
            TransactionPort transactionPort) {
        return new CreateOrderUseCase(orderRepository, inventoryRepository, outboxPort, orderEventPort, transactionPort);
    }

    @Bean
    public OrderSagaOrchestrator orderSagaOrchestrator(
            CreateOrderUseCase createOrderUseCase,
            BillingPort billingPort,
            OrderRepositoryPort orderRepository,
            BillingPaymentTranslator billingPaymentTranslator) {
        return new OrderSagaOrchestrator(createOrderUseCase, billingPort, orderRepository, billingPaymentTranslator);
    }

    @Bean
    public PayOrderUseCase payOrderUseCase(OrderRepositoryPort orderRepository) {
        return new PayOrderUseCase(orderRepository);
    }

    @Bean
    public AddItemToOrderUseCase addItemToOrderUseCase(
            OrderRepositoryPort orderRepository,
            CatalogPort catalogPort,
            TransactionPort transactionPort) {
        return new AddItemToOrderUseCase(orderRepository, catalogPort, transactionPort);
    }

    @Bean
    public ApplyCouponUseCase applyCouponUseCase(OrderRepositoryPort orderRepository) {
        return new ApplyCouponUseCase(orderRepository);
    }

    @Bean
    public ListOrdersByCustomerUseCase listOrdersByCustomerUseCase(OrderRepositoryPort orderRepository) {
        return new ListOrdersByCustomerUseCase(orderRepository);
    }

    @Bean
    public SearchOrdersUseCase searchOrdersUseCase(OrderSearchPort searchPort) {
        return new SearchOrdersUseCase(searchPort);
    }
}
