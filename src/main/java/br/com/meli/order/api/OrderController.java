package br.com.meli.order.api;

import br.com.meli.order.api.dto.CreateOrderRequest;
import br.com.meli.order.api.dto.OrderResponse;
import br.com.meli.order.application.ListOrdersByCustomerUseCase;
import br.com.meli.order.application.PayOrderUseCase;
import br.com.meli.order.application.PlaceOrderCommand;
import br.com.meli.order.application.SearchOrdersUseCase;
import br.com.meli.order.application.saga.OrderSagaOrchestrator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

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
        // SOLUÇÃO: logs JSON com MDC — cada log carrega correlationId automaticamente.
        // logger.info emitira com "correlationId":"...", "timestamp":"...", tudo em JSON.
        MDC.put("customerId", request.getCustomerId());
        logger.info("Creating order");
        
        PlaceOrderCommand command = toCommand(request);
        OrderResponse response = OrderResponse.from(sagaOrchestrator.execute(command));
        
        logger.info("Order created successfully");
        MDC.remove("customerId");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<OrderResponse> listByCustomer(@RequestParam String customerId) {
        // PROBLEMA: logs sem contexto de usuario — apos timeout, nao sabemos quem foi afetado.
        logger.info("Fetching orders for customer");
        List<OrderResponse> orders = listOrdersByCustomerUseCase.execute(customerId).stream()
                .map(OrderResponse::from)
                .toList();
        logger.info("Orders fetched");
        return orders;
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
