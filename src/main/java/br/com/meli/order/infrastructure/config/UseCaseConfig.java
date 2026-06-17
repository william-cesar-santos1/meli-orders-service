package br.com.meli.order.infrastructure.config;

import br.com.meli.order.application.*;
import br.com.meli.order.application.acl.BillingPaymentTranslator;
import br.com.meli.order.application.port.out.*;
import br.com.meli.order.application.saga.OrderSagaOrchestrator;
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
            SaveOrderPort saveOrderPort,
            FindInventoryPort findInventoryPort,
            SaveInventoryPort saveInventoryPort,
            OutboxPort outboxPort,
            TransactionPort transactionPort) {
        return new CreateOrderUseCase(saveOrderPort, findInventoryPort, saveInventoryPort, outboxPort, transactionPort);
    }

    @Bean
    public OrderSagaOrchestrator orderSagaOrchestrator(
            CreateOrderUseCase createOrderUseCase,
            BillingPort billingPort,
            SaveOrderPort saveOrderPort,
            BillingPaymentTranslator billingPaymentTranslator) {
        return new OrderSagaOrchestrator(createOrderUseCase, billingPort, saveOrderPort, billingPaymentTranslator);
    }

    @Bean
    public PayOrderUseCase payOrderUseCase(FindOrderPort findOrderPort, SaveOrderPort saveOrderPort) {
        return new PayOrderUseCase(findOrderPort, saveOrderPort);
    }

    @Bean
    public AddItemToOrderUseCase addItemToOrderUseCase(
            FindOrderPort findOrderPort,
            SaveOrderPort saveOrderPort,
            CatalogPort catalogPort,
            TransactionPort transactionPort) {
        return new AddItemToOrderUseCase(findOrderPort, saveOrderPort, catalogPort, transactionPort);
    }

    @Bean
    public ApplyCouponUseCase applyCouponUseCase(FindOrderPort findOrderPort) {
        return new ApplyCouponUseCase(findOrderPort);
    }

    @Bean
    public ListOrdersByCustomerUseCase listOrdersByCustomerUseCase(FindOrderPort findOrderPort) {
        return new ListOrdersByCustomerUseCase(findOrderPort);
    }

    @Bean
    public SearchOrdersUseCase searchOrdersUseCase(OrderSearchPort searchPort) {
        return new SearchOrdersUseCase(searchPort);
    }
}
