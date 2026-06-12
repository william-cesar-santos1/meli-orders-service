package br.com.meli.orders.application;

import br.com.meli.orders.api.BillingClient;
import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.billing.PaymentStatus;

// SOLUÇÃO: @Service removido — configurado via UseCaseConfig (infrastructure/config).
// O caso de uso e um POJO puro sem dependencias de framework.
// Principio: Dependency Rule — camadas internas nao conhecem frameworks externos.
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

