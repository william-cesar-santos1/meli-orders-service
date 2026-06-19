package br.com.meli.order.api;

import br.com.meli.order.api.dto.CreateOrderRequest;
import br.com.meli.order.api.dto.OrderResponse;
import br.com.meli.order.application.ListOrdersByCustomerUseCase;
import br.com.meli.order.application.PayOrderUseCase;
import br.com.meli.order.application.PlaceOrderCommand;
import br.com.meli.order.application.saga.OrderSagaOrchestrator;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.annotation.Timed;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderSagaOrchestrator sagaOrchestrator;
    private final PayOrderUseCase payOrderUseCase;
    private final ListOrdersByCustomerUseCase listOrdersByCustomerUseCase;
    private final MeterRegistry meterRegistry;

    public OrderController(OrderSagaOrchestrator sagaOrchestrator,
                           PayOrderUseCase payOrderUseCase,
                           ListOrdersByCustomerUseCase listOrdersByCustomerUseCase,
                           MeterRegistry meterRegistry) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.payOrderUseCase = payOrderUseCase;
        this.listOrdersByCustomerUseCase = listOrdersByCustomerUseCase;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping
    @Timed(value = "order.creation", description = "Tempo de criacao de pedido")
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request) {
        // SOLUÇÃO: logs JSON com MDC — cada log carrega correlationId automaticamente.
        // logger.info emitira com "correlationId":"...", "timestamp":"...", tudo em JSON.
        MDC.put("customerId", request.customerId());

        // SOLUÇÃO: counter de tentativa de criacao.
        meterRegistry.counter("orders.creation.attempt").increment();
        logger.info("Creating order");
        
        try {
            PlaceOrderCommand command = toCommand(request);
            OrderResponse response = OrderResponse.from(sagaOrchestrator.execute(command));

            // SOLUÇÃO: counter de sucesso.
            meterRegistry.counter("orders.created", "customerId", request.customerId()).increment();
            logger.info("Order created successfully");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            // SOLUÇÃO: counter de erro correlaciona tipo de excecao.
            meterRegistry.counter("orders.creation.error", "errorType", e.getClass().getSimpleName()).increment();
            logger.error("Order creation failed", e);
            throw e;
        } finally {
            MDC.remove("customerId");
        }
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
