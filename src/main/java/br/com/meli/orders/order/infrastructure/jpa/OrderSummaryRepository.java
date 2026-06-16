package br.com.meli.orders.order.infrastructure.jpa;

import br.com.meli.orders.order.domain.OrderSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// SOLUÇÃO: repositorio Spring Data dedicado para projecoes de resumo.
// Separar este repositorio do OrderRepository evita misturar
// queries de leitura leve com queries de escrita e leitura completa.
public interface OrderSummaryRepository extends JpaRepository<OrderEntity, Long> {

    // SOLUÇÃO: projecao JPQL retorna apenas os campos necessarios (orderId, customerId, status, total).
    // Evita carregar o grafo completo de OrderEntity + OrderItemEntity desnecessariamente.
    @Query("SELECT new br.com.meli.orders.order.domain.OrderSummary(o.id, o.customerId, o.status, o.totalAmount) " +
           "FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderSummary> findSummaryById(@Param("id") Long id);
}

