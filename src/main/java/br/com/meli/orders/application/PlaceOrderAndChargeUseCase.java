package br.com.meli.orders.application;

import br.com.meli.orders.api.BillingClient;
import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.PaymentStatus;
import org.springframework.stereotype.Service;

// PROBLEMA: fluxo distribuido implementado de forma linear sem qualquer mecanismo de
// compensacao. Se a cobranca (BillingClient) falhar apos o pedido ter sido criado,
// o pedido permanece no banco com status CREATED sem nunca ser cancelado —
// gerando um "pedido orfao" (orphan order). Em sistemas distribuidos,
// falhas parciais sao inevitaveis. Sem compensacao (Saga Pattern),
// o estado do sistema fica inconsistente. Principio violado: atomicidade
// de transacoes distribuidas.
@Service
public class PlaceOrderAndChargeUseCase {

    private final CreateOrderUseCase createOrderUseCase;
    private final BillingClient billingClient;
    private final OrderRepositoryPort orderRepository;

    public PlaceOrderAndChargeUseCase(CreateOrderUseCase createOrderUseCase,
                                       BillingClient billingClient,
                                       OrderRepositoryPort orderRepository) {
        this.createOrderUseCase = createOrderUseCase;
        this.billingClient = billingClient;
        this.orderRepository = orderRepository;
    }

    public Order execute(CreateOrderRequest request) {
        // Passo 1: cria o pedido
        Order order = createOrderUseCase.execute(request);

        // Passo 2: cobra o pagamento — PROBLEMA: se esta chamada falhar (timeout,
        // erro 500, rede indisponivel), o pedido ja foi criado e salvo no banco.
        // Nao ha nenhum rollback, cancelamento ou notificacao — o pedido fica
        // "preso" no estado CREATED para sempre. Isso e uma inconsistencia de dados
        // em um sistema distribuido sem compensacao.
        PaymentStatus paymentResult = billingClient.charge(order.id(), order.totalAmount());

        // Passo 3: marca o pedido como pago — PROBLEMA: se billingClient.charge() lancar
        // excecao, este passo nunca e executado, porem o pedido ja existe no banco.
        // Sem compensacao, nao ha como saber se o cliente foi cobrado ou nao.
        if (PaymentStatus.CAPTURED.equals(paymentResult)) {
            return orderRepository.updateStatus(order.id(), OrderStatus.PAID);
        }

        // PROBLEMA: retorna o pedido sem status de pagamento correto e
        // sem cancelar o pedido quando o pagamento falha.
        return order;
    }
}

