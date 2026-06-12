package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.CatalogPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.ProductInfo;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import br.com.meli.orders.domain.exceptions.ProductUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AddItemToOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;

    public AddItemToOrderUseCase(OrderRepositoryPort orderRepository, CatalogPort catalogPort) {
        this.orderRepository = orderRepository;
        this.catalogPort = catalogPort;
    }

    // verifica o catálogo primeiro — exceção de catálogo tem prioridade sobre pedido não encontrado
    @Transactional
    public Order execute(Long orderId, String productId, int quantity) {
        ProductInfo product = catalogPort.getProduct(productId);
        if (!product.available()) {
            throw new ProductUnavailableException(productId);
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderItem newItem = new OrderItem(null, productId, quantity, BigDecimal.ZERO, product.name());
        List<OrderItem> updated = new ArrayList<>(order.items());
        updated.add(newItem);
        return orderRepository.save(
            new Order(order.id(), order.customerId(), updated,
                order.status(), order.totalAmount(), order.createdAt(), order.paymentStatus()));
    }
}

