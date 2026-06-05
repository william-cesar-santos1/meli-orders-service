package br.com.meli.orders.infrastructure.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Map;

// SOLUÇÃO (Bloco 3 — MongoDB / schema flexível): o catálogo tem atributos
// que variam por categoria (eletrônico: voltagem; roupa: tamanho, cor; livro: ISBN).
// No modelo relacional, isso exigiria EAV (Entity-Attribute-Value) ou JSONB —
// soluções trabalhosas ou com perda de tipagem. O documento MongoDB acomoda
// estruturas variáveis nativamente, sem schema fixo.
// A fonte de verdade continua sendo o PostgreSQL para dados transacionais;
// o MongoDB serve o catálogo de leitura (read model), tolerando consistência eventual.
@Document(collection = "product_catalog")
public class ProductDocument {

    @Id
    private String id;
    private String name;
    private BigDecimal price;
    private String category;

    // atributos variam por categoria — sem schema fixo
    private Map<String, Object> attributes;

    public ProductDocument() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}

