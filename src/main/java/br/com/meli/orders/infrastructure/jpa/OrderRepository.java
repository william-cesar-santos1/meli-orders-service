package br.com.meli.orders.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    // Mantido para uso em buscas internas (ex: OrderSearchAdapter)
    List<OrderEntity> findByCustomerId(String customerId);

    // SOLUCAO (Bloco 2 — N+1): JOIN FETCH instrui o Hibernate a buscar
    // os itens junto com o pedido em uma unica query SQL.
    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.items WHERE o.customerId = :customerId")
    List<OrderEntity> findWithItemsByCustomer(@Param("customerId") String customerId);

    @Query("SELECT DISTINCT o FROM OrderEntity o JOIN o.items i WHERE LOWER(i.productName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<OrderEntity> findByProductDescriptionContaining(@Param("q") String q);
}

