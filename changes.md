# Prompt — Geração do Projeto Meli Orders Service (Aula 4)

Use este prompt para criar os branches de problemas e soluções da **Aula 4 — Testes Avançados: JUnit 5, Testcontainers & Carga** a partir do repositório `https://github.com/william-cesar-santos1/meli-orders-service` (branch `main`).

---

## Contexto geral

O projeto é o **Meli Orders Service**, Spring Boot 3.3.4 / Java 21, package raiz `br.com.meli.orders`. O estado atual do repositório tem:

- `domain/`: `Order` (record), `OrderItem` (record), `OrderStatus` (enum), `InventoryItem`
- `application/`: `CreateOrderUseCase`, `PayOrderUseCase`, `ListOrdersByCustomerUseCase`, `SearchOrdersUseCase`
- `application/port/out/`: `OrderRepositoryPort`, `InventoryRepositoryPort`, `OrderEventPort`, `OrderIndexPort`, `OrderSearchPort`, `OutboxPort`
- `api/`: `OrderController`, `IdempotencyFilter`; DTO `CreateOrderRequest(String customerId, List<Item> items)` onde `Item(String productId, int quantity, BigDecimal unitPrice, String productName)`
- `infrastructure/jpa/`: `OrderRepository`, `InventoryRepository` (Spring Data JPA), `OrderJpaAdapter`, `InventoryJpaAdapter`, `OrderEntity`, `OrderItemEntity`, `InventoryEntity`
- `infrastructure/performance/`: `OrderSimulation.java` (Gatling) com 3 cenários — `POST /orders`, `GET /orders?customerId=`, `GET /orders/search?q=` — **sem** bloco `.assertions(...)`
- `pom.xml` já inclui: `spring-boot-starter-test`, `testcontainers:postgresql/mongodb/elasticsearch`, `gatling-charts-highcharts` + `gatling-maven-plugin`

Esta aula **adiciona** classes de domínio novas e uma camada de testes avançados sobre o projeto existente.

---

## Estrutura de branches

| Branch | Conteúdo |
|--------|----------|
| `main` | `ApplyCouponUseCase` + `DiscountCalculator` (com bug), testes estáticos sem combinações, `CreateOrderUseCaseTest` com H2, `OrderSimulation` sem assertions |
| `feature/junit5-advanced-4` | Solução Bloco 1 — fix do bug no `ApplyCouponUseCase`, `@TestFactory` com CSV, `@Tag`, timeout assertions, Pitest MSI ≥ 85% |
| `feature/testcontainers-wiremock-5` | Solução Bloco 2 — Testcontainers com PostgreSQL real, `CatalogServiceWireMockTest` |
| `feature/load-testing-6` | Solução Bloco 3 — remove Gatling, adiciona k6 `blackfriday.js`, Prometheus, Grafana |

> **Encadeamento:** `feature/testcontainers-wiremock-5` é criado a partir de `feature/junit5-advanced-4`; `feature/load-testing-6` é criado a partir de `feature/testcontainers-wiremock-5`.
>
> **Numeração global:** sufixos 4, 5, 6 continuam a sequência do repositório (aula 5 usou 1, 2, 3).

**Regra de comentários:** `// PROBLEMA:` no `main`; `// SOLUÇÃO:` nas branches de feature. Explica sempre o conceito, nunca a sintaxe.

**Idioma:** código em inglês; comentários explicativos em português BR.

---

## Dependências a adicionar ao `pom.xml`

Adicionar **apenas o que não existe** no `pom.xml` atual:

```xml
<!-- Bloco main: H2 para plantar o problema do banco em memória.
     Removido em feature/testcontainers-wiremock-5. -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- Bloco 2: spring-boot-testcontainers e junit-jupiter
     (postgresql/mongodb/elasticsearch já existem) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Bloco 2: WireMock para simular falhas do serviço de catálogo -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.3.1</version>
    <scope>test</scope>
</dependency>

<!-- Bloco 1: Pitest — adicionar dentro de <build><plugins> -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.16.1</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.1</version>
        </dependency>
    </dependencies>
    <configuration>
        <targetClasses>
            <param>br.com.meli.orders.domain.*</param>
            <param>br.com.meli.orders.application.ApplyCouponUseCase</param>
        </targetClasses>
        <mutationThreshold>85</mutationThreshold>
    </configuration>
</plugin>
```

---

## Classes de produção a criar (novas — em todos os branches a partir de `main`)

Criar os seguintes arquivos antes dos commits de branch. Eles existem em todos os branches.

### `domain/Money.java`

```java
package br.com.meli.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount cannot be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? ZERO : new Money(result);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor));
    }
}
```

### `domain/Coupon.java`

```java
package br.com.meli.orders.domain;

public sealed interface Coupon permits CategoryCoupon, MinValueCoupon, PercentageCoupon {
    // calcula o desconto a partir do total informado
    Money calculateDiscount(Money total);
}
```

### `domain/CategoryCoupon.java`

```java
package br.com.meli.orders.domain;

import java.math.BigDecimal;

// Cupom de categoria: aplica percentual de desconto sobre o total do pedido
public record CategoryCoupon(String category, BigDecimal discountRate) implements Coupon {

    @Override
    public Money calculateDiscount(Money total) {
        return total.multiply(discountRate);
    }
}
```

### `domain/MinValueCoupon.java`

```java
package br.com.meli.orders.domain;

import java.math.BigDecimal;

// Cupom de valor mínimo: desconto fixo aplicado apenas se o total atingir o minValue
public record MinValueCoupon(BigDecimal minValue, BigDecimal discountAmount) implements Coupon {

    @Override
    public Money calculateDiscount(Money total) {
        if (total.amount().compareTo(minValue) >= 0) {
            return new Money(discountAmount);
        }
        return Money.ZERO;
    }
}
```

### `domain/PercentageCoupon.java`

```java
package br.com.meli.orders.domain;

import java.math.BigDecimal;

// Cupom percentual: aplica percentual de desconto sobre o total
public record PercentageCoupon(BigDecimal discountRate) implements Coupon {

    @Override
    public Money calculateDiscount(Money total) {
        return total.multiply(discountRate);
    }
}
```

### `domain/ProductInfo.java`

```java
package br.com.meli.orders.domain;

// Informações de produto retornadas pelo serviço de catálogo externo
public record ProductInfo(String id, String name, boolean available) {}
```

### `domain/exceptions/ProductUnavailableException.java`

```java
package br.com.meli.orders.domain.exceptions;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(Object productId) {
        super("Produto indisponível: " + productId);
    }
}
```

### `domain/exceptions/CatalogServiceUnavailableException.java`

```java
package br.com.meli.orders.domain.exceptions;

public class CatalogServiceUnavailableException extends RuntimeException {
    public CatalogServiceUnavailableException(Object productId) {
        super("Serviço de catálogo indisponível ao buscar produto: " + productId);
    }
}
```

### `application/port/out/CatalogPort.java`

```java
package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.ProductInfo;

public interface CatalogPort {
    ProductInfo getProduct(String productId);
}
```

### `application/AddItemToOrderUseCase.java`

```java
package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.CatalogPort;
import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderItem;
import br.com.meli.orders.domain.ProductInfo;
import br.com.meli.orders.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import br.com.meli.orders.domain.exceptions.ProductUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AddItemToOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;

    public AddItemToOrderUseCase(OrderRepositoryPort orderRepository, CatalogPort catalogPort) {
        this.orderRepository = orderRepository;
        this.catalogPort = catalogPort;
    }

    // verifica o catálogo primeiro — exceção de catálogo tem prioridade sobre pedido não encontrado
    @Transactional
    public Order execute(Long orderId, String productId, int quantity) {
        ProductInfo product = catalogPort.getProduct(productId);
        if (!product.available()) {
            throw new ProductUnavailableException(productId);
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderItem newItem = new OrderItem(null, productId, quantity, BigDecimal.ZERO, product.name());
        List<OrderItem> updated = new ArrayList<>(order.items());
        updated.add(newItem);
        return orderRepository.save(
            new Order(order.id(), order.customerId(), updated,
                order.status(), order.totalAmount(), order.createdAt()));
    }
}
```

### `infrastructure/catalog/HttpCatalogAdapter.java`

```java
package br.com.meli.orders.infrastructure.catalog;

import br.com.meli.orders.application.port.out.CatalogPort;
import br.com.meli.orders.domain.ProductInfo;
import br.com.meli.orders.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.orders.domain.exceptions.ProductUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpCatalogAdapter implements CatalogPort {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String catalogBaseUrl;

    public HttpCatalogAdapter(@Value("${services.catalog.url}") String catalogBaseUrl) {
        this.catalogBaseUrl = catalogBaseUrl;
    }

    @Override
    public ProductInfo getProduct(String productId) {
        String url = catalogBaseUrl + "/products/" + productId;
        try {
            return restTemplate.getForObject(url, ProductInfo.class);
        } catch (HttpClientErrorException e) {
            // 4xx: produto indisponível ou não encontrado no catálogo
            throw new ProductUnavailableException(productId);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 5xx ou timeout: serviço de catálogo instável
            throw new CatalogServiceUnavailableException(productId);
        }
    }
}
```

### `application.yml` — adição ao arquivo existente

```yaml
services:
  catalog:
    url: http://localhost:8081
```

---

## Branch `main` — Problemas plantados

### `application/ApplyCouponUseCase.java` — com bug

```java
package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Coupon;
import br.com.meli.orders.domain.Money;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplyCouponUseCase {

    private final OrderRepositoryPort orderRepository;

    public ApplyCouponUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    // PROBLEMA: os descontos de todos os cupons são calculados sobre o total ORIGINAL.
    // Com dois cupons combinados (ex: CategoryCoupon + MinValueCoupon), ambos descontam
    // sobre o mesmo valor base — o desconto total excede o esperado.
    // Exatamente o bug que causou R$ 1,8 M de prejuízo na Promofy em produção.
    public Money execute(Long orderId, List<Coupon> coupons) {
        if (coupons == null) {
            throw new IllegalArgumentException("Lista de cupons não pode ser nula");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        Money original = new Money(order.totalAmount());
        Money totalDiscount = Money.ZERO;
        for (Coupon coupon : coupons) {
            totalDiscount = totalDiscount.add(coupon.calculateDiscount(original)); // BUG: sempre sobre original
        }
        return original.subtract(totalDiscount);
    }
}
```

### Arquivos de teste a criar no `main`

```
src/test/java/br/com/meli/orders/
├── application/
│   ├── ApplyCouponUseCaseTest.java         ← testes estáticos, um cupom por vez
│   └── CreateOrderUseCaseTest.java         ← integração com H2
src/test/resources/
├── application-test.yml                    ← configura H2
└── test-data/
    └── coupon-combinations.csv             ← apenas casos de cupom único
```

### `application/ApplyCouponUseCaseTest.java` — testes estáticos

```java
package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// PROBLEMA: cada tipo de cupom é testado em isolamento com dados literais fixos.
// Nenhum cenário cobre a combinação de dois cupons no mesmo pedido —
// exatamente o caso que causou R$ 1,8 M de prejuízo em produção.
// O Pitest reporta MSI de ~40%: mutações no loop de descontos passam invisíveis.
@ExtendWith(MockitoExtension.class)
class ApplyCouponUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @InjectMocks
    private ApplyCouponUseCase useCase;

    private void givenOrderWithTotal(String total) {
        Order order = new Order(1L, "customer-test", List.of(), OrderStatus.CREATED,
            new BigDecimal(total), Instant.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    }

    @Test
    void shouldApplyCategoryDiscount() {
        givenOrderWithTotal("100.00");
        Money result = useCase.execute(1L, List.of(new CategoryCoupon("ELETRONICOS", new BigDecimal("0.10"))));
        assertThat(result).isEqualTo(new Money(new BigDecimal("90.00")));
    }

    @Test
    void shouldApplyMinValueDiscount() {
        givenOrderWithTotal("200.00");
        Money result = useCase.execute(1L, List.of(new MinValueCoupon(new BigDecimal("150.00"), new BigDecimal("20.00"))));
        assertThat(result).isEqualTo(new Money(new BigDecimal("180.00")));
    }

    @Test
    void shouldApplyPercentageCoupon() {
        givenOrderWithTotal("100.00");
        Money result = useCase.execute(1L, List.of(new PercentageCoupon(new BigDecimal("0.15"))));
        assertThat(result).isEqualTo(new Money(new BigDecimal("85.00")));
    }
}
```

### `application/CreateOrderUseCaseTest.java` — usando H2

```java
package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// PROBLEMA: H2 em memória não reproduz o comportamento de lock do PostgreSQL real.
// O SELECT ... FOR UPDATE SKIP LOCKED que protege o estoque é ignorado pelo H2.
// Requisições concorrentes para o mesmo produto passam no CI e esgotam estoque em produção.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CreateOrderUseCaseTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void shouldCreateOrder() {
        // PROBLEMA: o id pode ser null porque o SEQUENCE do PostgreSQL não é reproduzido pelo H2.
        // O teste passa mas não verifica o comportamento real de persistência.
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-alice",
            List.of(new CreateOrderRequest.Item("prod-tenis", 1, new BigDecimal("350.00"), "Tênis Nike"))
        );
        var order = createOrderUseCase.execute(request);
        assertThat(order).isNotNull();
    }
}
```

### `src/test/resources/application-test.yml`

```yaml
# PROBLEMA: banco em memória substitui o PostgreSQL nos testes de integração.
# Comportamento de lock, SEQUENCE e dialeto SQL divergem do banco real.
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
  data:
    mongodb:
      uri: mongodb://localhost:27017/orders_test
  elasticsearch:
    uris: http://localhost:9200
```

### `src/test/resources/test-data/coupon-combinations.csv`

```csv
description,order_total,coupon_types,expected_total
category discount 10%,100.00,CATEGORY:ELETRONICOS:0.10,90.00
min value discount R$20,200.00,MIN_VALUE:150.00:20.00,180.00
percentage 15%,100.00,PERCENTAGE:0.15,85.00
```

### `OrderSimulation.java` — manter sem `.assertions(...)` (já existe no repo assim)

O arquivo já existe sem `.assertions(...)`. Adicionar o comentário de problema:

```java
// PROBLEMA: sem assertions o Gatling sempre termina com sucesso independente da latência.
// Degradação de performance não quebra o build — qualquer p95 é aceito no CI.
// Performance fica invisível até o incidente em produção.
```

**Commit:** `aula-4: base — ApplyCouponUseCase com bug, testes estáticos sem combinações, H2 em testes`

---

## Branch `feature/junit5-advanced-4` — Solução Bloco 1

Criado a partir de `main`.

### `application/ApplyCouponUseCase.java` — bug corrigido

```java
package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Coupon;
import br.com.meli.orders.domain.Money;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplyCouponUseCase {

    private final OrderRepositoryPort orderRepository;

    public ApplyCouponUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    // SOLUÇÃO: cada cupom calcula o desconto sobre o total CORRENTE — não sobre o original.
    // O segundo cupom desconta sobre o valor já reduzido pelo primeiro.
    // A composição de descontos é correta para qualquer número de cupons.
    public Money execute(Long orderId, List<Coupon> coupons) {
        if (coupons == null) {
            throw new IllegalArgumentException("Lista de cupons não pode ser nula");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        Money total = new Money(order.totalAmount());
        for (Coupon coupon : coupons) {
            total = total.subtract(coupon.calculateDiscount(total)); // SOLUÇÃO: total corrente
        }
        return total;
    }
}
```

### `application/ApplyCouponUseCaseTest.java` — com @TestFactory

```java
package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.when;

// SOLUÇÃO: @TestFactory gera um caso de teste por linha do CSV — incluindo combinações duplas.
// Novos cenários de negócio entram no CSV sem tocar no código Java de teste.
// Cada caso falha de forma isolada no relatório do JUnit, facilitando o diagnóstico.
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ApplyCouponUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @InjectMocks
    private ApplyCouponUseCase useCase;

    @TestFactory
    Stream<DynamicTest> discountCombinations() throws Exception {
        return CouponCombinationLoader.load("test-data/coupon-combinations.csv")
            .map(scenario -> dynamicTest(scenario.description(), () -> {
                // mock local por cenário — evita contaminação de estado entre dynamic tests
                OrderRepositoryPort repo = Mockito.mock(OrderRepositoryPort.class);
                ApplyCouponUseCase uc = new ApplyCouponUseCase(repo);
                Order order = new Order(1L, "customer-test", List.of(), OrderStatus.CREATED,
                    scenario.orderTotal().amount(), Instant.now());
                when(repo.findById(1L)).thenReturn(Optional.of(order));
                Money result = uc.execute(1L, scenario.coupons());
                assertThat(result).isEqualTo(scenario.expectedTotal());
            }));
    }

    @Test
    @Tag("unit")
    void shouldRejectNullCouponList() {
        // SOLUÇÃO: assertTimeoutPreemptively garante que o cálculo não excede 100ms.
        // Detecta regressões de desempenho silenciosas antes que cheguem ao load test.
        // A validação de null ocorre antes do acesso ao repositório — nenhum stub necessário.
        assertTimeoutPreemptively(Duration.ofMillis(100), () ->
            assertThatThrownBy(() -> useCase.execute(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
        );
    }
}
```

### `domain/CouponCombinationLoader.java` (em `src/test/java/`)

```java
package br.com.meli.orders.domain;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

// SOLUÇÃO: desacopla os dados de teste do código de teste.
// Novos cenários não exigem recompilação nem alteração de código Java.
public final class CouponCombinationLoader {

    private CouponCombinationLoader() {}

    public static Stream<CouponScenario> load(String resourcePath) throws Exception {
        URL resource = CouponCombinationLoader.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Arquivo de cenários não encontrado: " + resourcePath);
        }
        List<String> lines = Files.readAllLines(Path.of(resource.toURI()));
        return lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(CouponScenario::parse);
    }
}
```

### `domain/CouponScenario.java` (em `src/test/java/`)

```java
package br.com.meli.orders.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

// SOLUÇÃO: record imutável que representa um cenário lido do CSV.
// Formato: description,order_total,coupon_types,expected_total
// coupon_types: TYPE:param1:param2 separados por | para múltiplos cupons
public record CouponScenario(String description, Money orderTotal,
                              List<Coupon> coupons, Money expectedTotal) {

    public static CouponScenario parse(String csvLine) {
        String[] parts = csvLine.split(",", 4);
        String description = parts[0].trim();
        Money orderTotal = new Money(new BigDecimal(parts[1].trim()));
        List<Coupon> coupons = Arrays.stream(parts[2].trim().split("\\|"))
            .map(CouponScenario::parseCoupon)
            .toList();
        Money expectedTotal = new Money(new BigDecimal(parts[3].trim()));
        return new CouponScenario(description, orderTotal, coupons, expectedTotal);
    }

    private static Coupon parseCoupon(String token) {
        String[] f = token.split(":");
        return switch (f[0].trim()) {
            case "CATEGORY"   -> new CategoryCoupon(f[1].trim(), new BigDecimal(f[2].trim()));
            case "MIN_VALUE"  -> new MinValueCoupon(new BigDecimal(f[1].trim()), new BigDecimal(f[2].trim()));
            case "PERCENTAGE" -> new PercentageCoupon(new BigDecimal(f[1].trim()));
            default -> throw new IllegalArgumentException("Tipo de cupom desconhecido: " + f[0]);
        };
    }
}
```

### `src/test/resources/test-data/coupon-combinations.csv` — expandido com combinações

```csv
description,order_total,coupon_types,expected_total
category discount 10%,100.00,CATEGORY:ELETRONICOS:0.10,90.00
min value discount R$20,200.00,MIN_VALUE:150.00:20.00,180.00
percentage 15%,100.00,PERCENTAGE:0.15,85.00
category + percentage sem duplicar,100.00,CATEGORY:ELETRONICOS:0.10|PERCENTAGE:0.05,85.50
min value + category segunda aplica sobre total reduzido,200.00,MIN_VALUE:150.00:20.00|CATEGORY:ELETRONICOS:0.10,162.00
percentage + min value ordem importa,150.00,PERCENTAGE:0.10|MIN_VALUE:100.00:15.00,120.00
three coupons combined,300.00,CATEGORY:ELETRONICOS:0.10|PERCENTAGE:0.05|MIN_VALUE:200.00:10.00,247.25
no discount when below min value,80.00,MIN_VALUE:100.00:20.00,80.00
```

### `src/test/resources/junit-platform.properties`

```properties
# SOLUÇÃO: testes @Tag("unit") rodam em paralelo para feedback rápido a cada commit.
# Testes @Tag("integration") rodam sequencialmente para evitar conflitos de estado no banco.
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

**Commit:** `feat: junit5-advanced — fix ApplyCouponUseCase, @TestFactory com CSV, @Tag, timeout assertions e Pitest`

---

## Branch `feature/testcontainers-wiremock-5` — Solução Bloco 2

Criado a partir de `feature/junit5-advanced-4`.

### Mudanças no `pom.xml`

**Remover** a dependência H2:

```xml
<!-- REMOVER -->
<!-- <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency> -->
```

### `application/CreateOrderUseCaseIT.java` (substitui `CreateOrderUseCaseTest.java`)

```java
package br.com.meli.orders.application;

import br.com.meli.orders.api.dto.CreateOrderRequest;
import br.com.meli.orders.domain.exceptions.OutOfStockException;
import br.com.meli.orders.infrastructure.jpa.InventoryEntity;
import br.com.meli.orders.infrastructure.jpa.InventoryRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// SOLUÇÃO: o teste roda contra PostgreSQL 16 real em container descartável.
// O SELECT ... FOR UPDATE SKIP LOCKED que protege o estoque é exercitado pelo banco real.
// withReuse(true) elimina o cold-start de ~3s em execuções locais repetidas.
@SpringBootTest
@Testcontainers
@Tag("integration")
class CreateOrderUseCaseIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private CreateOrderUseCase createOrderUseCase;
    @Autowired private InventoryRepository inventoryRepository;

    @BeforeEach
    void seedInventory() {
        // insere produto com estoque = 1 para exercitar o lock concorrente
        inventoryRepository.save(new InventoryEntity("prod-limited", 1));
    }

    @Test
    void shouldCreateOrderAndReturnPersistedId() {
        // SOLUÇÃO: o id é gerado pela SEQUENCE real do PostgreSQL — nunca é null como no H2.
        inventoryRepository.save(new InventoryEntity("prod-tenis", 10));
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-alice",
            List.of(new CreateOrderRequest.Item("prod-tenis", 1, new BigDecimal("350.00"), "Tênis Nike"))
        );
        var order = createOrderUseCase.execute(request);
        assertThat(order.id()).isNotNull().isPositive();
    }

    @Test
    void shouldPreventDuplicateActiveOrderForSameCustomer() {
        // SOLUÇÃO: o SELECT ... FOR UPDATE SKIP LOCKED garante que apenas uma das duas
        // requisições concorrentes consome o estoque de qty=1.
        // Com H2, ambos os pedidos teriam sucesso — o bug de concorrência fica invisível no CI.
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-concurrent",
            List.of(new CreateOrderRequest.Item("prod-limited", 1, new BigDecimal("100.00"), "Produto Limitado"))
        );

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
            try { createOrderUseCase.execute(request); return true; }
            catch (OutOfStockException e) { return false; }
        });
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> {
            try { createOrderUseCase.execute(request); return true; }
            catch (OutOfStockException e) { return false; }
        });

        long successCount = Stream.of(first, second)
            .mapToLong(f -> { try { return f.get() ? 1L : 0L; } catch (Exception e) { return 0L; } })
            .sum();

        assertThat(successCount).isEqualTo(1);
    }
}
```

### `application/CatalogServiceWireMockTest.java`

```java
package br.com.meli.orders.application;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import br.com.meli.orders.domain.exceptions.CatalogServiceUnavailableException;
import br.com.meli.orders.domain.exceptions.ProductUnavailableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// SOLUÇÃO: WireMock simula o serviço de catálogo com cenários de falha offline.
// O teste não depende do serviço real estar no ar — reproduzível em qualquer ambiente de CI.
@SpringBootTest
@Testcontainers
@Tag("integration")
class CatalogServiceWireMockTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @DynamicPropertySource
    static void configureInfra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // SOLUÇÃO: aponta o catálogo para o WireMock — porta dinâmica elimina conflito no CI.
        registry.add("services.catalog.url", wireMock::baseUrl);
    }

    @Autowired
    private AddItemToOrderUseCase addItemToOrderUseCase;

    @Test
    void shouldRejectUnavailableProduct() {
        // SOLUÇÃO: WireMock retorna 422 — o use case lança ProductUnavailableException.
        // Verifica o tipo exato da exceção, não apenas que "alguma coisa" foi lançada.
        wireMock.stubFor(get(urlPathMatching("/products/.*"))
            .willReturn(aResponse().withStatus(422)
                .withBody("{\"available\": false}")));

        assertThatThrownBy(() -> addItemToOrderUseCase.execute(1L, "prod-test", 1))
            .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void shouldHandleCatalogServiceUnavailable() {
        // SOLUÇÃO: simula instabilidade do serviço externo com 503 + delay de 3s.
        // Verifica que o use case lança CatalogServiceUnavailableException
        // em vez de travar a thread indefinidamente.
        wireMock.stubFor(get(urlPathMatching("/products/.*"))
            .willReturn(aResponse().withStatus(503).withFixedDelay(3000)));

        assertThatThrownBy(() -> addItemToOrderUseCase.execute(1L, "prod-test", 1))
            .isInstanceOf(CatalogServiceUnavailableException.class);
    }
}
```

### `src/test/resources/application-test.yml` — sem H2

```yaml
# SOLUÇÃO: H2 removido. O datasource e o catálogo são injetados via @DynamicPropertySource.
# Nenhuma infraestrutura é configurada estaticamente — cada teste controla a sua.
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  data:
    mongodb:
      uri: mongodb://localhost:27017/orders_test
  elasticsearch:
    uris: http://localhost:9200
```

**Commit:** `feat: testcontainers-wiremock — PostgreSQL real em containers e WireMock com caos de rede`

---

## Branch `feature/load-testing-6` — Solução Bloco 3

Criado a partir de `feature/testcontainers-wiremock-5`.

### Mudanças no `pom.xml` — remover Gatling

```xml
<!-- REMOVER: Gatling substituído por k6 neste branch.
     k6 é executado externamente — não precisa de dependência Maven. -->

<!-- Remover dependência: -->
<!-- <dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    ...
</dependency> -->

<!-- Remover plugin: -->
<!-- <plugin>
    <groupId>io.gatling</groupId>
    <artifactId>gatling-maven-plugin</artifactId>
    ...
</plugin> -->
```

### Deletar `src/test/java/br/com/meli/orders/performance/OrderSimulation.java`

```bash
# k6 substitui o Gatling para load test neste branch.
# OrderSimulation.java é removido junto com as dependências do Gatling.
git rm src/test/java/br/com/meli/orders/performance/OrderSimulation.java
```

### `k6/blackfriday.js`

```javascript
// SOLUÇÃO: thresholds definem os budgets como critério de aceite do build.
// Violação de p95 > 300ms ou erro > 1% retorna exit code 1 — o CI falha automaticamente.
// Performance passa a ser um requisito verificável, não um desejo pós-deploy.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '3m', target: 1000 }, // ramp-up: simula abertura de campanha
        { duration: '5m', target: 1000 }, // plateau: valida estabilidade sob carga sustentada
        { duration: '1m', target: 0 },    // ramp-down: verifica recuperação sem degradação
    ],
    thresholds: {
        'http_req_duration': ['p(95)<300'],
        'http_req_failed':   ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const customerId = `customer-${Math.floor(Math.random() * 10000)}`;

    const res = http.post(
        `${BASE_URL}/orders`,
        JSON.stringify({
            customerId: customerId,
            items: [
                {
                    productId: 'prod-tenis',
                    quantity: 1,
                    unitPrice: 350.00,
                    productName: 'Tênis Nike Air Max'
                }
            ]
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': `${customerId}-${Date.now()}`,
            },
        }
    );

    check(res, {
        'order created': (r) => r.status === 201,
        'within budget': (r) => r.timings.duration < 300,
    });

    sleep(0.05);
}
```

### Adições ao `docker-compose.yml`

```yaml
  prometheus:
    image: prom/prometheus:v2.51.2
    container_name: meli-orders-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro

  grafana:
    image: grafana/grafana:10.4.2
    container_name: meli-orders-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus
```

### `monitoring/prometheus.yml`

```yaml
global:
  scrape_interval: 5s

scrape_configs:
  - job_name: meli-orders
    static_configs:
      - targets: ['host.docker.internal:8080']
    metrics_path: /actuator/prometheus

rule_files:
  - alerts.yml
```

### `monitoring/alerts.yml`

```yaml
groups:
  - name: sla
    rules:
      - alert: HighLatencyP95
        # dispara se o p95 ficar acima de 300ms por mais de 2 minutos
        expr: http_server_requests_seconds{quantile="0.95"} > 0.3
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "p95 acima do budget de 300ms — investigar antes da próxima campanha"
```

**Commit:** `feat: load-testing — k6 blackfriday script, remove Gatling, Prometheus e Grafana`

---

## Sequência de comandos Git

Execute dentro do diretório do projeto. **Não faça push.**

```bash
# Branch main — adicionar as novas classes de produção + problemas plantados
git checkout main
# Criar: Money.java, Coupon.java, CategoryCoupon.java, MinValueCoupon.java, PercentageCoupon.java
# Criar: ProductInfo.java, exceptions/ProductUnavailableException.java, exceptions/CatalogServiceUnavailableException.java
# Criar: port/out/CatalogPort.java, ApplyCouponUseCase.java (com bug), AddItemToOrderUseCase.java
# Criar: infrastructure/catalog/HttpCatalogAdapter.java
# Adicionar a application.yml: services.catalog.url
# Criar testes: ApplyCouponUseCaseTest.java (estáticos), CreateOrderUseCaseTest.java (H2)
# Criar: application-test.yml, test-data/coupon-combinations.csv (3 linhas)
# Modificar pom.xml: adicionar H2 test scope
# Adicionar comentário PROBLEMA ao OrderSimulation.java
git add .
git commit -m "aula-4: base — ApplyCouponUseCase com bug, testes estáticos sem combinações, H2 em testes"

# Branch de solução 1 — JUnit 5 avançado (a partir de main)
git checkout -b feature/junit5-advanced-4
# Modificar: ApplyCouponUseCase.java (fix do bug)
# Substituir: ApplyCouponUseCaseTest.java (com @TestFactory)
# Criar (em src/test/): CouponCombinationLoader.java, CouponScenario.java
# Substituir: coupon-combinations.csv (8 linhas com combinações)
# Criar: junit-platform.properties
# Modificar pom.xml: adicionar plugin Pitest
git add .
git commit -m "feat: junit5-advanced — fix ApplyCouponUseCase, @TestFactory com CSV, @Tag, timeout assertions e Pitest"

# Branch de solução 2 — Testcontainers e WireMock (a partir da solução 1)
git checkout -b feature/testcontainers-wiremock-5
# Modificar pom.xml: remover H2, adicionar spring-boot-testcontainers, junit-jupiter, wiremock-standalone
# Substituir: CreateOrderUseCaseTest.java → CreateOrderUseCaseIT.java
# Criar: CatalogServiceWireMockTest.java
# Substituir: application-test.yml (sem H2)
git add .
git commit -m "feat: testcontainers-wiremock — PostgreSQL real em containers e WireMock com caos de rede"

# Branch de solução 3 — Load testing (a partir da solução 2)
git checkout -b feature/load-testing-6
# Modificar pom.xml: remover dependência e plugin Gatling
# Deletar: src/test/java/br/com/meli/orders/performance/OrderSimulation.java
# Criar: k6/blackfriday.js
# Modificar: docker-compose.yml (adicionar prometheus e grafana)
# Criar: monitoring/prometheus.yml, monitoring/alerts.yml
git add .
git commit -m "feat: load-testing — k6 blackfriday script, remove Gatling, Prometheus e Grafana"
```

---

## Validações obrigatórias antes de cada commit

- **Build:** `mvn package -q` deve passar sem erros em todos os branches (Docker deve estar rodando para Testcontainers funcionar)
- **Docker:** `docker compose up -d && docker compose ps` deve mostrar todos os serviços como `running`; encerrar com `docker compose down`
- **Pitest** (a partir de `feature/junit5-advanced-4`): `mvn test-compile pitest:mutationCoverage` deve reportar MSI ≥ 85% para `br.com.meli.orders.domain.*`
- **k6** (apenas em `feature/load-testing-6`): `k6 run k6/blackfriday.js --vus 1 --duration 5s` deve executar sem erro de sintaxe no script (o threshold pode falhar com 1 VU — isso é esperado)
