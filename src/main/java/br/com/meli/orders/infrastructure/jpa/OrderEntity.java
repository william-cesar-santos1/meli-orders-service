package br.com.meli.orders.infrastructure.jpa;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    // PROBLEMA: IDENTITY força o Hibernate a buscar o ID gerado após cada INSERT,
    // emitindo um round-trip por insert. Isso quebra o batch insert —
    // 500 inserts resultam em 500 round-trips ao banco.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // PROBLEMA: sem @Version, não há controle de versão otimista.
    // Duas transações concorrentes que leem e modificam o mesmo pedido
    // podem sobrescrever silenciosamente a mudança uma da outra (lost update).

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OrderItemEntity> items = new ArrayList<>();

    public OrderEntity() {}

    public static OrderEntity from(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.customerId = order.getCustomerId();
        entity.status = order.getStatus().name();
        entity.totalAmount = order.getTotalAmount();
        entity.createdAt = order.getCreatedAt();
        return entity;
    }

    public Order toDomain() {
        Order order = new Order();
        order.setId(this.id);
        order.setCustomerId(this.customerId);
        order.setStatus(OrderStatus.valueOf(this.status));
        order.setTotalAmount(this.totalAmount);
        order.setCreatedAt(this.createdAt);
        return order;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<OrderItemEntity> getItems() { return items; }
    public void setItems(List<OrderItemEntity> items) { this.items = items; }
}

