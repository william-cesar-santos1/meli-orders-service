package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.port.out.InventoryRepositoryPort;
import br.com.meli.orders.application.port.out.OrderIndexPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.InventoryItem;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.exceptions.OutOfStockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final OrderIndexPort orderIndexPort;

    public CreateOrderUseCase(OrderRepositoryPort orderRepository,
                               InventoryRepositoryPort inventoryRepository,
                               OrderIndexPort orderIndexPort) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderIndexPort = orderIndexPort;
    }

    // SOLUÇÃO (Bloco 1 — ACID): @Transactional envolve todo o método em uma única
    // transação do banco de dados. Se qualquer operação falhar, o banco reverte
    // automaticamente todas as mudanças já feitas (ROLLBACK).
    // Pedido e decremento de inventário ocorrem juntos ou não ocorrem — nunca metade.
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
        Order saved = orderRepository.save(order); // PostgreSQL — confirmado

        // agora, se qualquer linha abaixo falhar, o pedido acima também é revertido
        for (CreateOrderRequest.Item item : request.items()) {
            // PROBLEMA: leitura e escrita sem proteção de concorrência (resolvido no Bloco 2).
            InventoryItem inv = inventoryRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new OutOfStockException(item.productId()));

            if (inv.quantity() < item.quantity()) {
                throw new OutOfStockException(item.productId());
            }
            inventoryRepository.save(inv.withQuantity(inv.quantity() - item.quantity()));
        }

        // PROBLEMA: o pedido é gravado no PostgreSQL e indexado no Elasticsearch
        // em operações separadas, sem transação distribuída entre os dois.
        // Se o Elasticsearch estiver fora do ar, o pedido existirá na fonte de verdade
        // mas não no índice de busca — inconsistência silenciosa.
        orderIndexPort.index(saved); // Elasticsearch — pode falhar

        return saved;
    }
}


