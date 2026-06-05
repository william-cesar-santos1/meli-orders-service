package br.com.meli.orders.infrastructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductCatalogRepository extends MongoRepository<ProductDocument, String> {

    Optional<ProductDocument> findByName(String name);
}

