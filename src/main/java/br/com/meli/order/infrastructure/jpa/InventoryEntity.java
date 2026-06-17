package br.com.meli.order.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer quantity;

    public InventoryEntity() {}

    /** Construtor de conveniência usado nos testes — usa productId como nome padrão. */
    public InventoryEntity(String productId, int quantity) {
        this.productId = productId;
        this.name = productId;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}

