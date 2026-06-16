package br.com.meli.orders.order.api;

import br.com.meli.orders.order.application.ListOrdersByCustomerUseCase;
import br.com.meli.orders.order.application.PayOrderUseCase;
import br.com.meli.orders.order.application.PlaceOrderCommand;
import br.com.meli.orders.order.application.SearchOrdersUseCase;
import br.com.meli.orders.order.application.saga.OrderSagaOrchestrator;
import br.com.meli.orders.order.api.dto.CreateOrderRequest;
import br.com.meli.orders.order.api.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderSagaOrchestrator sagaOrchestrator;
    private final PayOrderUseCase payOrderUseCase;
    private final SearchOrdersUseCase searchOrdersUseCase;
    private final ListOrdersByCustomerUseCase listOrdersByCustomerUseCase;

    public OrderController(OrderSagaOrchestrator sagaOrchestrator,
                           PayOrderUseCase payOrderUseCase,
                           SearchOrdersUseCase searchOrdersUseCase,
                           ListOrdersByCustomerUseCase listOrdersByCustomerUseCase) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.payOrderUseCase = payOrderUseCase;
        this.searchOrdersUseCase = searchOrdersUseCase;
        this.listOrdersByCustomerUseCase = listOrdersByCustomerUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request) {
        // Controller converte DTO de API → Comando de aplicação (Dependency Inversion)
        PlaceOrderCommand command = toCommand(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(sagaOrchestrator.execute(command)));
    }

    @GetMapping
    public List<OrderResponse> listByCustomer(@RequestParam String customerId) {
        return listOrdersByCustomerUseCase.execute(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> pay(@PathVariable Long id) {
        return ResponseEntity.ok(OrderResponse.from(payOrderUseCase.execute(id)));
    }

    @GetMapping("/search")
    public List<OrderResponse> search(@RequestParam String q) {
        return searchOrdersUseCase.search(q).stream()
                .map(OrderResponse::from)
                .toList();
    }

    private PlaceOrderCommand toCommand(CreateOrderRequest request) {
        return new PlaceOrderCommand(
                request.customerId(),
                request.items().stream()
                        .map(i -> new PlaceOrderCommand.Item(
                                i.productId(), i.quantity(), i.unitPrice(), i.productName()))
                        .toList()
        );
    }
}
