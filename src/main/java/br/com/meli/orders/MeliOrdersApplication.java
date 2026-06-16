package br.com.meli.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = {
        "br.com.meli.orders.infrastructure.jpa",
        "br.com.meli.orders.infrastructure.outbox"
})
@EnableElasticsearchRepositories(basePackages = "br.com.meli.orders.infrastructure.search")
public class MeliOrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeliOrdersApplication.class, args);
    }
}
