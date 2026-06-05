package br.com.meli.orders.infrastructure.search;

import br.com.meli.orders.infrastructure.jpa.OrderEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

// SOLUÇÃO (Bloco 3 — Elasticsearch): este documento é uma projeção otimizada
// para leitura — estrutura diferente do OrderEntity, projetada para busca.
// O índice invertido do Elasticsearch encontra documentos por termo em O(1),
// independente do volume total. Ao contrário do LIKE no SQL, o analyzer
// 'portuguese' faz stemming: 'tênis', 'tenis' e 'Tênis' são equivalentes na busca.
@Document(indexName = "orders")
public class OrderSearchDocument {

    @Id
    private String id;

    // SOLUÇÃO: Text com analyzer 'portuguese' habilita busca full-text
    // com stemming, remoção de stopwords e normalização de acentos.
    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String customerName;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String productDescription;

    // SOLUÇÃO: Keyword é indexado como string exata — usado para filtros,
    // não para busca textual. Adequado para status, IDs, categorias.
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Double)
    private BigDecimal totalAmount;

    public OrderSearchDocument() {}

    public static OrderSearchDocument from(OrderEntity entity) {
        OrderSearchDocument doc = new OrderSearchDocument();
        doc.id = entity.getId() != null ? entity.getId().toString() : null;
        doc.customerName = entity.getCustomerId();
        doc.status = entity.getStatus();
        doc.createdAt = entity.getCreatedAt();
        doc.totalAmount = entity.getTotalAmount();
        // productDescription é populado com dados dos itens apenas no Bloco 3
        doc.productDescription = "";
        return doc;
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductDescription() { return productDescription; }
    public void setProductDescription(String productDescription) { this.productDescription = productDescription; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}

