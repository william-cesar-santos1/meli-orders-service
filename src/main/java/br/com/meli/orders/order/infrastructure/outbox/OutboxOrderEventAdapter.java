package br.com.meli.orders.order.infrastructure.outbox;

import br.com.meli.orders.order.application.port.out.OrderEventPort;
import br.com.meli.orders.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// SOLUÇÃO: substitui o OrderEventMongoAdapter (removido nesta branch).
// Neste bounded context, o registro de eventos de auditoria ocorre via
// Outbox Pattern (OutboxPort), que persiste eventos no PostgreSQL de forma
// atomica com a transacao principal. Este adapter de log serve como fallback
// simples enquanto o mecanismo de publicacao de eventos e finalizado.
// Principio: eliminacao de dependencia desnecessaria de infraestrutura (MongoDB)
// do fluxo principal de dominio.
@Component
public class OutboxOrderEventAdapter implements OrderEventPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxOrderEventAdapter.class);

    @Override
    public void recordOrderPlaced(Order order) {
        // SOLUÇÃO: evento ja foi gravado na tabela outbox pelo CreateOrderUseCase.
        // Este adapter apenas registra o log para rastreabilidade.
        // A publicacao real para consumidores externos e feita pelo OutboxProcessor.
        log.info("Evento OrderPlaced registrado via outbox para pedido id={} customerId={}",
                order.id(), order.customerId());
    }
}

