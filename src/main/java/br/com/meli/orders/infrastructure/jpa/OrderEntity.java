package br.com.meli.orders.infrastructure.jpa;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    // SOLUCAO (Bloco 2 — batch insert): SEQUENCE com allocationSize pre-aloca
    // blocos de IDs na memoria da aplicacao. O Hibernate nao precisa de round-trip
    // ao banco para obter o ID apos cada INSERT — ele ja tem os proximos 50 IDs reservados.
    // Combinado com hibernate.jdbc.batch_size=50, reduz 500 round-trips para ~10.
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "orders_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // SOLUCAO (Bloco 2 — optimistic locking): @Version faz o Hibernate incluir
    // a coluna version no WHERE do UPDATE. Se outra transacao ja alterou o registro,
    // o UPDATE afeta 0 linhas e o Hibernate lanca OptimisticLockException — sem lost update.
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OrderItemEntity> items = new ArrayList<>();

    public OrderEntity() {}

    public static OrderEntity from(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.customerId = order.customerId();
        entity.status = order.status().name();
        entity.totalAmount = order.totalAmount();
        entity.createdAt = order.createdAt();

        if (order.items() != null && !order.items().isEmpty()) {
            List<OrderItemEntity> itemEntities = order.items().stream()
                    .map(item -> {
                        OrderItemEntity ie = new OrderItemEntity();
                        ie.setOrder(entity);
                        ie.setProductId(item.productId());
                        ie.setProductName(item.productName() != null ? item.productName() : item.productId());
                        ie.setQuantity(item.quantity());
                        ie.setUnitPrice(item.unitPrice());
                        return ie;
                    })
                    .collect(Collectors.toList());
            entity.items = itemEntities;
        }
        return entity;
    }

    public Order toDomain() {
        List<OrderItem> domainItems = items.stream()
                .map(ie -> new OrderItem(
                        String.valueOf(ie.getId()),
                        ie.getProductId(),
                        ie.getQuantity(),
                        ie.getUnitPrice(),
                        ie.getProductName()
                ))
                .toList();
        return new Order(id, customerId, domainItems, OrderStatus.valueOf(status), totalAmount, createdAt, br.com.meli.orders.domain.PaymentStatus.PENDING);
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

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<OrderItemEntity> getItems() { return items; }
    public void setItems(List<OrderItemEntity> items) { this.items = items; }
}
