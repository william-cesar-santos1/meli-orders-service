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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/orders")
public class OrderController {

    // PROBLEMA: o cache de idempotência vive apenas na memória desta instância.
    // Com 3 pods em produção, cada pod tem seu próprio mapa.
    // A mesma Idempotency-Key pode ser aceita em pods diferentes,
    // processando a mesma operação múltiplas vezes.
    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

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

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateOrderRequest request) {

        if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
            return ResponseEntity.ok(
                    OrderResponse.fromJson(idempotencyCache.get(idempotencyKey))
            );
        }

        Order order = createOrderUseCase.execute(request);
        OrderResponse response = OrderResponse.from(order);

        if (idempotencyKey != null) {
            idempotencyCache.put(idempotencyKey, response.toJson());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
