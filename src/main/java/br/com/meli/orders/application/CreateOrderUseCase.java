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

    // PROBLEMA: sem @Transactional, o pedido e o decremento de inventário
    // são operações independentes. Se o decremento falhar após o pedido ser gravado,
    // o banco fica em estado inconsistente: pedido existe, estoque não foi decrementado.
    // Em produção isso gera overselling silencioso.
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

        // se qualquer linha abaixo lançar exceção, o pedido acima já está confirmado no banco
        for (CreateOrderRequest.Item item : request.items()) {
            // PROBLEMA: leitura e escrita sem proteção de concorrência.
            // race condition: outra thread pode passar pela verificação abaixo ao mesmo tempo,
            // ler quantity = 1 e decrementar — overselling.
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
