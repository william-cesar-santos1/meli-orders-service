package br.com.meli.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = {
        "br.com.meli.order.infrastructure.jpa",
        "br.com.meli.order.infrastructure.outbox"
})
public class MeliOrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeliOrdersApplication.class, args);
    }
}
