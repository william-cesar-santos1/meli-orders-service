package br.com.meli.orders.api;

import br.com.meli.orders.application.CreateOrderUseCase;
import br.com.meli.orders.application.ListOrdersByCustomerUseCase;
import br.com.meli.orders.application.PayOrderUseCase;
import br.com.meli.orders.application.SearchOrdersUseCase;
import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.api.dto.OrderResponse;
import br.com.meli.orders.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final PayOrderUseCase payOrderUseCase;
    private final SearchOrdersUseCase searchOrdersUseCase;
    private final ListOrdersByCustomerUseCase listOrdersByCustomerUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           PayOrderUseCase payOrderUseCase,
                           SearchOrdersUseCase searchOrdersUseCase,
                           ListOrdersByCustomerUseCase listOrdersByCustomerUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.payOrderUseCase = payOrderUseCase;
        this.searchOrdersUseCase = searchOrdersUseCase;
        this.listOrdersByCustomerUseCase = listOrdersByCustomerUseCase;
    }

    // SOLUÇÃO: controlador sem qualquer logica de idempotencia —
    // essa responsabilidade foi completamente delegada ao IdempotencyFilter com Redis.
    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request) {
        Order order = createOrderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
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
}
