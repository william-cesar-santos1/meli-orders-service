package br.com.meli.orders.infrastructure.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface OrderSearchRepository extends ElasticsearchRepository<OrderSearchDocument, String> {

    List<OrderSearchDocument> findByProductDescriptionContaining(String q);
}

