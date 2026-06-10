package br.com.meli.orders.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Simulação de performance do meli-orders-service.
 *
 * Três cenários que exercitam exatamente os gargalos trabalhados em cada bloco:
 *
 *   createOrders   — POST /orders: mede o impacto do @Transactional (Bloco 1),
 *                    do batch insert + SEQUENCE (Bloco 2) e do Outbox (Bloco 3).
 *
 *   listByCustomer — GET /orders?customerId=...: reproduz o N+1 no `main` e mostra
 *                    a melhora após o JOIN FETCH (Bloco 2).
 *
 *   searchOrders   — GET /orders/search?q=...: demonstra o full table scan no `main`
 *                    (LIKE '%term%') versus a busca Elasticsearch (Bloco 3).
 *
 * Como executar ao final de cada branch:
 *   mvn gatling:test
 *
 * O relatório HTML é gerado em target/gatling/ordersimulation-<timestamp>/index.html
 */
public class OrderSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // -----------------------------------------------------------------------
    // Cenário 1 — criação de pedidos (exercita ACID, pool, batch, Outbox)
    // -----------------------------------------------------------------------
    ScenarioBuilder createOrders = scenario("Criar Pedidos")
            .exec(
                    http("POST /orders")
                            .post("/orders")
                            .header("Idempotency-Key", session -> UUID.randomUUID().toString())
                            .body(StringBody("""
                                    {
                                      "customerId": "customer-alice",
                                      "items": [
                                        {
                                          "productId": "prod-tenis",
                                          "quantity": 1,
                                          "unitPrice": 350.00,
                                          "productName": "Tênis Nike Air Max 42 azul"
                                        }
                                      ]
                                    }
                                    """))
                            .check(status().in(201, 200, 409))
            );

    // -----------------------------------------------------------------------
    // Cenário 2 — listagem por cliente (exercita N+1 vs JOIN FETCH)
    // -----------------------------------------------------------------------
    ScenarioBuilder listByCustomer = scenario("Listar Pedidos por Cliente")
            .exec(
                    http("GET /orders?customerId=customer-alice")
                            .get("/orders")
                            .queryParam("customerId", "customer-alice")
                            .check(status().is(200))
            );

    // -----------------------------------------------------------------------
    // Cenário 3 — busca full-text (exercita LIKE vs Elasticsearch)
    // -----------------------------------------------------------------------
    ScenarioBuilder searchOrders = scenario("Buscar Pedidos")
            .exec(
                    http("GET /orders/search?q=tenis")
                            .get("/orders/search")
                            .queryParam("q", "tenis")
                            .check(status().is(200))
            );

    // -----------------------------------------------------------------------
    // Perfil de carga — ramp de 10 s → 30 s sob carga constante
    // -----------------------------------------------------------------------
    {
        setUp(
                createOrders
                        .injectOpen(
                                rampUsers(20).during(Duration.ofSeconds(10)),
                                constantUsersPerSec(10).during(Duration.ofSeconds(30))
                        ),
                listByCustomer
                        .injectOpen(
                                rampUsers(20).during(Duration.ofSeconds(10)),
                                constantUsersPerSec(15).during(Duration.ofSeconds(30))
                        ),
                searchOrders
                        .injectOpen(
                                rampUsers(20).during(Duration.ofSeconds(10)),
                                constantUsersPerSec(15).during(Duration.ofSeconds(30))
                        )
        )
                .protocols(httpProtocol)
                // Critério de aceite: p95 < 500 ms e taxa de erro < 5 %
                // O `main` vai falhar este critério — é o ponto de partida da discussão.
                .assertions(
                        global().responseTime().percentile(95).lt(500),
                        global().failedRequests().percent().lt(5.0)
                );
    }
}
