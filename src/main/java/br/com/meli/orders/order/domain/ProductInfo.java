package br.com.meli.orders.order.domain;

// Informações de produto retornadas pelo serviço de catálogo externo
public record ProductInfo(String id, String name, boolean available) {}

