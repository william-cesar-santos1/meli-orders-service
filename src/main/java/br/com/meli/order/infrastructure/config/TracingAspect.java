package br.com.meli.order.infrastructure.config;

import br.com.meli.order.application.PlaceOrderCommand;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Aspecto de rastreabilidade: cria spans filhos automaticamente para todos os
 * casos de uso (application layer) e adapters de infraestrutura relevantes.
 *
 * MOTIVO: Use Cases são POJOs puros (sem @Service, sem @Observed) por exigência
 * da Clean Architecture. Instrumentá-los diretamente violaria as regras validadas
 * pelo ArchUnit. O aspecto fica na camada de infrastructure — único lugar onde
 * dependências de framework são aceitáveis.
 *
 * RESULTADO NO JAEGER:
 *   HTTP POST /orders
 *     └── OrderSagaOrchestrator.execute
 *           ├── CreateOrderUseCase.execute
 *           │     └── OutboxJpaAdapter.save
 *           ├── BillingHttpAdapter.charge
 *           │     └── HTTP POST /payments/charge (RestTemplate)
 *           └── (jdbc spans de datasource-micrometer)
 */
@Aspect
@Component
public class TracingAspect {

    private final Tracer tracer;

    public TracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    // ─── Application Layer ────────────────────────────────────────────────────

    /**
     * Instrumenta todos os Use Cases da camada de Application e o Saga Orchestrator.
     * Pointcut cobre: CreateOrderUseCase, PayOrderUseCase, AddItemToOrderUseCase,
     * ListOrdersByCustomerUseCase, ApplyCouponUseCase e OrderSagaOrchestrator.
     */
    @Around(
        "execution(* br.com.meli.order.application.*UseCase.execute(..)) || " +
        "execution(* br.com.meli.order.application.saga.OrderSagaOrchestrator.execute(..))"
    )
    public Object traceUseCases(ProceedingJoinPoint pjp) throws Throwable {
        String spanName = simpleClassName(pjp) + "." + pjp.getSignature().getName();
        ScopedSpan span = tracer.startScopedSpan(spanName);
        tagUseCaseArgs(span, pjp.getArgs());
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    // ─── Infrastructure Layer ─────────────────────────────────────────────────

    /**
     * Instrumenta o BillingHttpAdapter: adiciona tags billing.orderId e billing.amount
     * ao span, além do span HTTP filho criado automaticamente pelo RestTemplate.
     *
     * Sem este span, as tags de negócio (orderId, amount) ficariam invisíveis no Jaeger —
     * o span do RestTemplate só mostra URL e status HTTP.
     */
    @Around("execution(* br.com.meli.order.infrastructure.billing.BillingHttpAdapter.charge(..))")
    public Object traceBilling(ProceedingJoinPoint pjp) throws Throwable {
        ScopedSpan span = tracer.startScopedSpan("BillingHttpAdapter.charge");
        Object[] args = pjp.getArgs();
        if (args.length >= 2) {
            if (args[0] instanceof Long orderId)        span.tag("billing.orderId", orderId.toString());
            if (args[1] instanceof BigDecimal amount)   span.tag("billing.amount", amount.toPlainString());
        }
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * Instrumenta o OutboxJpaAdapter: mostra no Jaeger a publicação do evento de domínio
     * (ex: ORDER_CREATED) dentro da transação de criação do pedido.
     */
    @Around("execution(* br.com.meli.order.infrastructure.outbox.OutboxJpaAdapter.save(..))")
    public Object traceOutbox(ProceedingJoinPoint pjp) throws Throwable {
        ScopedSpan span = tracer.startScopedSpan("OutboxJpaAdapter.save");
        Object[] args = pjp.getArgs();
        if (args.length >= 2) {
            if (args[0] instanceof String aggregateId)  span.tag("outbox.aggregateId", aggregateId);
            if (args[1] instanceof String eventType)    span.tag("outbox.eventType", eventType);
        }
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Extrai o nome simples da classe real (não o proxy CGLIB). */
    private String simpleClassName(ProceedingJoinPoint pjp) {
        String name = pjp.getTarget().getClass().getSimpleName();
        // Remove sufixo CGLIB: "OrderSagaOrchestrator$$SpringCGLIB$$0" → "OrderSagaOrchestrator"
        int cglib = name.indexOf("$$");
        return cglib > 0 ? name.substring(0, cglib) : name;
    }

    /**
     * Adiciona tags ao span a partir dos argumentos do Use Case.
     * Cobre os tipos usados pelas assinaturas de todos os execute() do projeto.
     */
    private void tagUseCaseArgs(ScopedSpan span, Object[] args) {
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) continue;

            if (arg instanceof PlaceOrderCommand cmd) {
                // CreateOrderUseCase / OrderSagaOrchestrator
                span.tag("order.customerId", cmd.customerId());
                span.tag("order.items.count", String.valueOf(cmd.items().size()));

            } else if (arg instanceof Long id) {
                // PayOrderUseCase, AddItemToOrderUseCase, ApplyCouponUseCase
                span.tag("order.id", id.toString());

            } else if (arg instanceof String s && s.length() <= 256) {
                // ListOrdersByCustomerUseCase (customerId), AddItemToOrderUseCase (productId)
                span.tag("arg" + i, s);

            } else if (arg instanceof Integer n) {
                // AddItemToOrderUseCase (quantity)
                span.tag("arg" + i, n.toString());
            }
        }
    }
}

