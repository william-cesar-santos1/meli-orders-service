package br.com.meli.orders.infrastructure.config;

import br.com.meli.orders.application.CreateOrderUseCase;
import br.com.meli.orders.application.PlaceOrderAndChargeUseCase;
import br.com.meli.orders.application.port.out.InventoryRepositoryPort;
import br.com.meli.orders.application.port.out.OrderEventPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.application.port.out.OutboxPort;
import br.com.meli.orders.application.saga.OrderSagaOrchestrator;
import br.com.meli.orders.api.BillingClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// SOLUÇÃO: configuracao responsavel por instanciar os casos de uso como beans Spring.
// Os casos de uso sao POJOs puros — sem @Service.
// Principio: Dependency Rule (Clean Architecture) — frameworks ficam na camada mais externa.
@Configuration
public class UseCaseConfig {

    @Bean
    public CreateOrderUseCase createOrderUseCase(
            OrderRepositoryPort orderRepository,
            InventoryRepositoryPort inventoryRepository,
            OutboxPort outboxPort,
            OrderEventPort orderEventPort) {
        return new CreateOrderUseCase(orderRepository, inventoryRepository, outboxPort, orderEventPort);
    }

    @Bean
    public PlaceOrderAndChargeUseCase placeOrderAndChargeUseCase(
            CreateOrderUseCase createOrderUseCase,
            BillingClient billingClient,
            OrderRepositoryPort orderRepository) {
        return new PlaceOrderAndChargeUseCase(createOrderUseCase, billingClient, orderRepository);
    }

    // SOLUÇÃO: Saga Orchestrator registrado como bean — substitui PlaceOrderAndChargeUseCase
    // para fluxos que precisam de compensacao garantida.
    @Bean
    public OrderSagaOrchestrator orderSagaOrchestrator(
            CreateOrderUseCase createOrderUseCase,
            BillingClient billingClient,
            OrderRepositoryPort orderRepository) {
        return new OrderSagaOrchestrator(createOrderUseCase, billingClient, orderRepository);
    }
}
