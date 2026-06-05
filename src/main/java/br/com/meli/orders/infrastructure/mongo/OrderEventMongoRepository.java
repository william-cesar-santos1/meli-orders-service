package br.com.meli.orders.infrastructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderEventMongoRepository extends MongoRepository<OrderEventDocument, String> {
}

