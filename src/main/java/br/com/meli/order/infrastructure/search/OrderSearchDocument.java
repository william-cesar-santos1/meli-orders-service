package br.com.meli.order.infrastructure.search;

import br.com.meli.order.domain.order.Order;
import br.com.meli.order.domain.order.OrderStatus;
import br.com.meli.order.infrastructure.jpa.OrderEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderSearchDocument {

    private String id;

    private String customerName;

    private String productDescription;

    private String status;

    private Instant createdAt;

    private BigDecimal totalAmount;

    public OrderSearchDocument() {
    }

    public static OrderSearchDocument from(Order order) {
        OrderSearchDocument doc = new OrderSearchDocument();
        doc.id = order.id() != null ? order.id().toString() : null;
        doc.customerName = order.customerId();
        doc.status = order.status().name();
        doc.createdAt = order.createdAt();
        doc.totalAmount = order.totalAmount();
        doc.productDescription = order.items().stream()
                .map(i -> i.productName() != null ? i.productName() : i.productId())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
        return doc;
    }

    /**
     * Mantido para compatibilidade com código de infraestrutura que parte da entidade JPA
     */
    public static OrderSearchDocument from(OrderEntity entity) {
        OrderSearchDocument doc = new OrderSearchDocument();
        doc.id = entity.getId() != null ? entity.getId().toString() : null;
        doc.customerName = entity.getCustomerId();
        doc.status = entity.getStatus();
        doc.createdAt = entity.getCreatedAt();
        doc.totalAmount = entity.getTotalAmount();
        doc.productDescription = "";
        return doc;
    }

    public Order toDomain() {
        return new Order(
                id != null ? Long.parseLong(id) : null,
                customerName,
                java.util.List.of(),
                status != null ? OrderStatus.valueOf(status) : OrderStatus.CREATED,
                totalAmount,
                createdAt
        );
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar OrderSearchDocument", e);
        }
    }

    public static OrderSearchDocument fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(json, OrderSearchDocument.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao desserializar OrderSearchDocument", e);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
