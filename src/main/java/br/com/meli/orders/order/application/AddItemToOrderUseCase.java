package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.CatalogPort;
import br.com.meli.orders.order.application.port.out.FindOrderPort;
import br.com.meli.orders.order.application.port.out.SaveOrderPort;
import br.com.meli.orders.order.application.port.out.TransactionPort;
import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.OrderItem;
import br.com.meli.orders.order.domain.ProductInfo;
import br.com.meli.orders.order.domain.exceptions.OrderNotFoundException;
import br.com.meli.orders.order.domain.exceptions.ProductUnavailableException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Caso de uso: adicionar item a um pedido existente via catálogo.
 * POJO puro — sem anotações de framework.
 */
public class AddItemToOrderUseCase {

    private final FindOrderPort findOrderPort;
    private final SaveOrderPort saveOrderPort;
    private final CatalogPort catalogPort;
    private final TransactionPort transactionPort;

    public AddItemToOrderUseCase(
            FindOrderPort findOrderPort,
            SaveOrderPort saveOrderPort,
            CatalogPort catalogPort,
            TransactionPort transactionPort) {
        this.findOrderPort = findOrderPort;
        this.saveOrderPort = saveOrderPort;
        this.catalogPort = catalogPort;
        this.transactionPort = transactionPort;
    }

    // verifica o catálogo primeiro — exceção de catálogo tem prioridade sobre pedido não encontrado
    public Order execute(Long orderId, String productId, int quantity) {
        ProductInfo product = catalogPort.getProduct(productId);
        if (!product.available()) {
            throw new ProductUnavailableException(productId);
        }
        return transactionPort.execute(() -> {
            Order order = findOrderPort.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));
            OrderItem newItem = new OrderItem(null, productId, quantity, BigDecimal.ZERO, product.name());
            List<OrderItem> updated = new ArrayList<>(order.items());
            updated.add(newItem);
            return saveOrderPort.save(
                    new Order(order.id(), order.customerId(), updated,
                            order.status(), order.totalAmount(), order.createdAt()));
        });
    }
}
