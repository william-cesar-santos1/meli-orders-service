package br.com.meli.orders.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    // PROBLEMA: este método retorna pedidos com a associação 'items' configurada
    // como LAZY (padrão do JPA). Quando o código itera sobre os itens de cada pedido,
    // o Hibernate emite 1 query extra por pedido — o problema N+1.
    // Com 100 pedidos: 101 queries ao banco por requisição.
    List<OrderEntity> findByCustomerId(String customerId);

    // PROBLEMA: LIKE '%q%' força um full table scan no PostgreSQL.
    // Sem índice de texto, cada busca percorre todos os registros da tabela.
    // Com 1 milhão de pedidos, a busca degrada linearmente.
    // Além disso, LIKE não faz stemming: 'tênis' não encontra 'tenis' ou 'Tênis'.
    @Query("SELECT DISTINCT o FROM OrderEntity o JOIN o.items i WHERE LOWER(i.productName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<OrderEntity> findByProductDescriptionContaining(@Param("q") String q);
}

