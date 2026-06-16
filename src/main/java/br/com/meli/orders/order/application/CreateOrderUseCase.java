package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.InventoryRepositoryPort;
import br.com.meli.orders.order.application.port.out.OrderEventPort;
import br.com.meli.orders.order.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.order.application.port.out.OutboxPort;
import br.com.meli.orders.order.application.port.out.TransactionPort;
import br.com.meli.orders.order.domain.InventoryItem;
import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.OrderItem;
import br.com.meli.orders.order.domain.exceptions.OutOfStockException;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: criar pedido com verificação de estoque.
 * POJO puro — sem anotações de framework. Registrado como @Bean em UseCaseConfig.
 * A transação é delegada ao TransactionPort (implementado em infrastructure com @Transactional).
 * Principio: Dependency Rule — camadas internas não conhecem frameworks externos.
 */
public class CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final OutboxPort outboxPort;
    private final OrderEventPort orderEventPort;
    private final TransactionPort transactionPort;

    public CreateOrderUseCase(OrderRepositoryPort orderRepository,
                               InventoryRepositoryPort inventoryRepository,
                               OutboxPort outboxPort,
                               OrderEventPort orderEventPort,
                               TransactionPort transactionPort) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxPort = outboxPort;
        this.orderEventPort = orderEventPort;
        this.transactionPort = transactionPort;
    }

    public Order execute(PlaceOrderCommand command) {
        return transactionPort.execute(() -> {
            List<OrderItem> items = command.items().stream()
                    .map(i -> new OrderItem(
                            UUID.randomUUID().toString(),
                            i.productId(),
                            i.quantity(),
                            i.unitPrice(),
                            i.productName() != null ? i.productName() : i.productId()
                    ))
                    .toList();

            Order order = Order.create(command.customerId(), items);
            Order saved = orderRepository.save(order);

            for (PlaceOrderCommand.Item item : command.items()) {
                InventoryItem inv = inventoryRepository.findByProductIdWithLock(item.productId())
                        .orElseThrow(() -> new OutOfStockException(item.productId()));

                if (inv.quantity() < item.quantity()) {
                    throw new OutOfStockException(item.productId());
                }
                inventoryRepository.save(inv.withQuantity(inv.quantity() - item.quantity()));
            }

            outboxPort.save(saved.id().toString(), "ORDER_CREATED", saved);
            orderEventPort.recordOrderPlaced(saved);
            return saved;
        });
    }
}
