package br.com.meli.order.domain;

public record InventoryItem(String productId, String name, int quantity) {

    public InventoryItem withQuantity(int newQuantity) {
        return new InventoryItem(productId, name, newQuantity);
    }
}

