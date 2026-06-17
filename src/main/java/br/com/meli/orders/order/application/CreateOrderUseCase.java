package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.*;
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

    private final SaveOrderPort orderRepository;
    private final FindInventoryPort findInventoryPort;
    private final SaveInventoryPort saveInventoryPort;
    private final OutboxPort outboxPort;
    private final TransactionPort transactionPort;

    public CreateOrderUseCase(SaveOrderPort saveOrderPort,
                              FindInventoryPort findInventoryPort,
                              SaveInventoryPort saveInventoryPort,
                              OutboxPort outboxPort,
                              TransactionPort transactionPort) {
        this.orderRepository = saveOrderPort;
        this.findInventoryPort = findInventoryPort;
        this.saveInventoryPort = saveInventoryPort;
        this.outboxPort = outboxPort;
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
                InventoryItem inv = findInventoryPort.findByProductIdWithLock(item.productId())
                        .orElseThrow(() -> new OutOfStockException(item.productId()));

                if (inv.quantity() < item.quantity()) {
                    throw new OutOfStockException(item.productId());
                }
                saveInventoryPort.save(inv.withQuantity(inv.quantity() - item.quantity()));
            }

            outboxPort.save(saved.id().toString(), "ORDER_CREATED", saved);
            return saved;
        });
    }
}
