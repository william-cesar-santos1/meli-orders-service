package br.com.meli.orders.api.dto;

import br.com.meli.orders.domain.Order;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String id,
        String customerId,
        String status,
        BigDecimal totalAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId() != null ? order.getId().toString() : null,
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar OrderResponse", e);
        }
    }

    public static OrderResponse fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(json, OrderResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao desserializar OrderResponse", e);
        }
    }
}

