package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.exceptions.OutOfStockException;
import br.com.meli.orders.infrastructure.jpa.InventoryEntity;
import br.com.meli.orders.infrastructure.jpa.InventoryRepository;
import br.com.meli.orders.infrastructure.jpa.OrderEntity;
import br.com.meli.orders.infrastructure.jpa.OrderItemEntity;
import br.com.meli.orders.infrastructure.jpa.OrderRepository;
import br.com.meli.orders.infrastructure.search.OrderSearchDocument;
import br.com.meli.orders.infrastructure.search.OrderSearchRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderSearchRepository searchRepository;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               InventoryRepository inventoryRepository,
                               OrderSearchRepository searchRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.searchRepository = searchRepository;
    }

    // PROBLEMA: sem @Transactional, o pedido e o decremento de inventário
    // são operações independentes. Se o decremento falhar após o pedido ser gravado,
    // o banco fica em estado inconsistente: pedido existe, estoque não foi decrementado.
    // Em produção isso gera overselling silencioso.
    public Order execute(CreateOrderRequest request) {
        Order order = Order.create(request);
        OrderEntity entity = OrderEntity.from(order);
        OrderEntity saved = orderRepository.save(entity);

        // se qualquer linha abaixo lançar exceção, o pedido acima já está confirmado no banco
        for (CreateOrderRequest.Item item : request.items()) {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrder(saved);
            itemEntity.setProductId(item.productId());
            itemEntity.setProductName(item.productName() != null ? item.productName() : item.productId());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            saved.getItems().add(itemEntity);

            // PROBLEMA: leitura e escrita sem proteção de concorrência
            // race condition: outra thread pode passar pela verificação abaixo ao mesmo tempo
            InventoryEntity inv = inventoryRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new OutOfStockException(item.productId()));

            if (inv.getQuantity() < item.quantity()) {
                throw new OutOfStockException(item.productId());
            }
            // race condition: outra thread pode ter decrementado entre o findByProductId e o save
            inv.setQuantity(inv.getQuantity() - item.quantity());
            inventoryRepository.save(inv);
        }

        orderRepository.save(saved);

        // PROBLEMA: o pedido é gravado no PostgreSQL e indexado no Elasticsearch
        // em operações separadas, sem transação distribuída entre os dois.
        // Se o Elasticsearch estiver fora do ar quando o PostgreSQL confirmar,
        // o pedido existirá na fonte de verdade mas não no índice de busca.
        // A busca retornará vazio para um pedido que existe — inconsistência silenciosa.
        searchRepository.save(OrderSearchDocument.from(saved));  // Elasticsearch — pode falhar

        order.setId(saved.getId());
        return order;
    }
}

