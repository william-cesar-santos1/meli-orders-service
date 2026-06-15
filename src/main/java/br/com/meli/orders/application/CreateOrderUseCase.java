package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.port.out.InventoryRepositoryPort;
import br.com.meli.orders.application.port.out.OrderEventPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.application.port.out.OutboxPort;
import br.com.meli.orders.domain.InventoryItem;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.exceptions.OutOfStockException;
import br.com.meli.orders.infrastructure.jpa.OrderRepository;
import br.com.meli.orders.infrastructure.search.OrderSearchDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// PROBLEMA: @Service eh uma anotacao do Spring Framework injetada diretamente no caso de uso.
// O dominio/aplicacao nao deve conhecer detalhes do framework — isso viola a Dependency Rule
// da Clean Architecture: camadas internas nao podem depender de frameworks de camadas externas.
// O caso de uso deveria ser um POJO puro, configurado pelo container de injecao na camada de infraestrutura.
@Service
public class CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final OutboxPort outboxPort;
    private final OrderEventPort orderEventPort;
    // PROBLEMA: SpringDataOrderRepository (detalhe de infraestrutura JPA) injetado diretamente
    // no caso de uso da camada de aplicacao. O caso de uso deveria depender apenas de
    // interfaces (portas de saida) — nunca de implementacoes concretas de infraestrutura.
    // Isso acopla a logica de negocio ao Spring Data e impede trocar o mecanismo de persistencia
    // sem alterar o caso de uso.
    private final OrderRepository springDataOrderRepository;

    public CreateOrderUseCase(OrderRepositoryPort orderRepository,
                               InventoryRepositoryPort inventoryRepository,
                               OutboxPort outboxPort,
                               OrderEventPort orderEventPort,
                               OrderRepository springDataOrderRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxPort = outboxPort;
        this.orderEventPort = orderEventPort;
        this.springDataOrderRepository = springDataOrderRepository;
    }

    @Transactional
    public Order execute(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(
                        UUID.randomUUID().toString(),
                        i.productId(),
                        i.quantity(),
                        i.unitPrice(),
                        i.productName() != null ? i.productName() : i.productId()
                ))
                .toList();

        Order order = Order.create(request.customerId(), items);
        Order saved = orderRepository.save(order);

        for (CreateOrderRequest.Item item : request.items()) {
            InventoryItem inv = inventoryRepository.findByProductIdWithLock(item.productId())
                    .orElseThrow(() -> new OutOfStockException(item.productId()));

            if (inv.quantity() < item.quantity()) {
                throw new OutOfStockException(item.productId());
            }
            inventoryRepository.save(inv.withQuantity(inv.quantity() - item.quantity()));
        }

        outboxPort.save(
                saved.id().toString(),
                "ORDER_CREATED",
                saved
        );

        // Log de evento no MongoDB para auditoria/replay (consistencia eventual aceita)
        orderEventPort.recordOrderPlaced(saved);

        return saved;
    }
}
