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
import br.com.meli.orders.infrastructure.search.OrderSearchDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final OutboxPort outboxPort;
    private final OrderEventPort orderEventPort;

    public CreateOrderUseCase(OrderRepositoryPort orderRepository,
                               InventoryRepositoryPort inventoryRepository,
                               OutboxPort outboxPort,
                               OrderEventPort orderEventPort) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxPort = outboxPort;
        this.orderEventPort = orderEventPort;
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

        // SOLUCAO (Bloco 3 — Outbox): em vez de chamar Elasticsearch diretamente,
        // grava o evento na tabela outbox dentro da MESMA transacao do Postgres.
        // Se o Elasticsearch estiver fora do ar, o pedido e salvo normalmente
        // e o evento fica na fila para ser processado quando o servico voltar.
        outboxPort.save(
                saved.id().toString(),
                "ORDER_CREATED",
                OrderSearchDocument.from(saved).toJson()
        );

        // Log de evento no MongoDB para auditoria/replay (consistencia eventual aceita)
        orderEventPort.recordOrderPlaced(saved);

        return saved;
    }
}
