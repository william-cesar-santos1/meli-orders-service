# Prompt — Geração do Projeto Meli Orders Service (Aula 3)

Use este prompt para gerar o projeto base e as branches de solução da Aula 3 — Persistência com JPA & NoSQL.

---

## Contexto geral

Gere um projeto Spring Boot 3.3.x chamado **meli-orders-service** que será usado em aula para demonstrar problemas reais de persistência e construir as soluções junto com os alunos.

O projeto tem **dois estados**:

1. **Branch `main`** — o projeto base, com os problemas propositalmente plantados. É o ponto de partida da aula. Os alunos vão identificar os problemas e construir as soluções bloco a bloco.
2. **Branches `feature/bloco-1`, `feature/bloco-2`, `feature/bloco-3`** — cada branch acumula as correções do bloco correspondente sobre a anterior. São reveladas apenas após a turma construir a solução.

**Regra para os comentários:** todo trecho relevante deve ter um comentário `// PROBLEMA:` no código base e `// SOLUÇÃO:` na branch de feature, explicando o que está errado/correto e por quê — sem mencionar sintaxe Java, apenas o conceito que o código demonstra.

---

## Dependências (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
</parent>

<dependencies>
    <!-- Web -->
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-validation</dependency>

    <!-- Persistência relacional -->
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>postgresql (runtime)</dependency>
    <dependency>flyway-core</dependency>

    <!-- NoSQL -->
    <dependency>spring-boot-starter-data-mongodb</dependency>
    <dependency>spring-boot-starter-data-elasticsearch</dependency>

    <!-- Cache / idempotência distribuída -->
    <dependency>spring-boot-starter-data-redis</dependency>

    <!-- Observabilidade -->
    <dependency>spring-boot-starter-actuator</dependency>
    <dependency>micrometer-registry-prometheus</dependency>

    <!-- Testes -->
    <dependency>spring-boot-starter-test (test)</dependency>
    <dependency>testcontainers (test) — postgresql, mongodb, elasticsearch</dependency>

    <!-- Performance -->
    <dependency>io.gatling.highcharts:gatling-charts-highcharts:3.11.5 (test)</dependency>
</dependencies>

<!-- Plugin para executar a simulação via Maven -->
<plugin>
    <groupId>io.gatling</groupId>
    <artifactId>gatling-maven-plugin</artifactId>
    <version>4.9.6</version>
    <configuration>
        <simulationClass>br.com.meli.orders.performance.OrderSimulation</simulationClass>
    </configuration>
</plugin>
```

---

## Estrutura de pacotes

```
src/main/java/br/com/meli/orders/
├── MeliOrdersApplication.java
├── domain/
│   ├── Order.java
│   ├── OrderItem.java
│   ├── OrderStatus.java           (enum: CREATED, PAID, SHIPPED, CANCELLED)
│   └── exceptions/
│       ├── OrderNotFoundException.java
│       └── OutOfStockException.java
├── application/
│   ├── CreateOrderUseCase.java
│   ├── PayOrderUseCase.java
│   └── SearchOrdersUseCase.java
├── infrastructure/
│   ├── jpa/
│   │   ├── OrderEntity.java
│   │   ├── OrderItemEntity.java
│   │   ├── InventoryEntity.java
│   │   ├── OrderRepository.java
│   │   ├── InventoryRepository.java
│   │   └── OutboxRepository.java
│   ├── mongo/
│   │   ├── ProductDocument.java
│   │   └── ProductCatalogRepository.java
│   ├── search/
│   │   ├── OrderSearchDocument.java
│   │   └── OrderSearchRepository.java
│   └── outbox/
│       ├── OutboxEntry.java
│       ├── OutboxRepository.java
│       └── OutboxProcessor.java
└── api/
    ├── OrderController.java
    ├── IdempotencyFilter.java
    ├── dto/
    │   ├── CreateOrderRequest.java
    │   └── OrderResponse.java
    └── GlobalExceptionHandler.java

src/test/java/br/com/meli/orders/
└── performance/
    └── OrderSimulation.java
```

---

## Branch `main` — projeto base (problemas plantados)

### O que o projeto base deve fazer

- Expor os endpoints funcionais (a aplicação sobe e responde).
- Conter os **problemas plantados** que serão corrigidos em aula.
- Cada problema deve ter um comentário `// PROBLEMA:` explicando o que está errado.

### Fixtures de dados

Popule via migration Flyway (`V1__seed.sql`):

```sql
-- 3 produtos no inventário
INSERT INTO inventory (product_id, name, quantity) VALUES
  ('prod-tenis',  'Tênis Nike Air Max 42 azul', 5),
  ('prod-camisa', 'Camisa Polo M branca',        10),
  ('prod-livro',  'Designing Data-Intensive Applications', 3);

-- 2 pedidos existentes
INSERT INTO orders (id, customer_id, status, total_amount, created_at) VALUES
  ('ord-001', 'customer-alice', 'PAID',    350.00, '2026-06-01 10:00:00'),
  ('ord-002', 'customer-bob',   'CREATED', 129.90, '2026-06-01 11:00:00');

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES
  ('item-001', 'ord-001', 'prod-tenis',  1, 350.00),
  ('item-002', 'ord-002', 'prod-livro',  1, 129.90);
```

---

### Problema 1 — Ausência de atomicidade (sem `@Transactional`)

**Arquivo:** `CreateOrderUseCase.java`

```java
@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               InventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
    }

    // PROBLEMA: sem @Transactional, o pedido e o decremento de inventário
    // são operações independentes. Se o decremento falhar após o pedido ser gravado,
    // o banco fica em estado inconsistente: pedido existe, estoque não foi decrementado.
    // Em produção isso gera overselling silencioso.
    public Order execute(CreateOrderRequest request) {
        Order order = Order.create(request);
        orderRepository.save(OrderEntity.from(order));

        // se esta linha lançar qualquer exceção, o pedido acima já está confirmado no banco
        inventoryRepository.decrement(request.productId(), request.quantity());

        return order;
    }
}
```

---

### Problema 2 — Idempotência local (não escala com múltiplos pods)

**Arquivo:** `OrderController.java`

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    // PROBLEMA: o cache de idempotência vive apenas na memória desta instância.
    // Com 3 pods em produção, cada pod tem seu próprio mapa.
    // A mesma Idempotency-Key pode ser aceita em pods diferentes,
    // processando a mesma operação múltiplas vezes.
    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

    private final CreateOrderUseCase createOrderUseCase;

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateOrderRequest request) {

        if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
            return ResponseEntity.ok(
                OrderResponse.fromJson(idempotencyCache.get(idempotencyKey))
            );
        }

        Order order = createOrderUseCase.execute(request);
        OrderResponse response = OrderResponse.from(order);

        if (idempotencyKey != null) {
            idempotencyCache.put(idempotencyKey, response.toJson());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

---

### Problema 3 — Pool de conexões sem limite explícito (backpressure não configurado)

**Arquivo:** `application.yml`

```yaml
spring:
  application:
    name: meli-orders-service
  datasource:
    url: jdbc:postgresql://localhost:5432/orders_db
    username: orders
    password: orders
    # PROBLEMA: sem configuração de HikariCP, o pool usa os defaults do Spring Boot
    # (maximum-pool-size = 10, connection-timeout = 30s).
    # Sob carga de 500 req/s, o pool se esgota silenciosamente.
    # Requisições ficam na fila até o timeout sem nenhum sinal claro ao cliente.
  jpa:
    hibernate:
      ddl-auto: validate
    # PROBLEMA: sem log de SQL habilitado, é impossível ver quantas queries
    # o Hibernate está gerando por requisição. O problema N+1 passa invisível.
    show-sql: false
```

---

### Problema 4 — N+1 em consulta de pedidos por cliente

**Arquivo:** `OrderRepository.java`

```java
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    // PROBLEMA: este método retorna pedidos com a associação 'items' configurada
    // como LAZY (padrão do JPA). Quando o código itera sobre os itens de cada pedido,
    // o Hibernate emite 1 query extra por pedido — o problema N+1.
    // Com 100 pedidos: 101 queries ao banco por requisição.
    List<OrderEntity> findByCustomerId(String customerId);
}
```

**Arquivo:** `OrderEntity.java`

```java
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    // PROBLEMA: IDENTITY força o Hibernate a buscar o ID gerado após cada INSERT,
    // emitindo um round-trip por insert. Isso quebra o batch insert —
    // 500 inserts resultam em 500 round-trips ao banco.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private String status;
    private BigDecimal totalAmount;
    private Instant createdAt;

    // PROBLEMA: sem @Version, não há controle de versão otimista.
    // Duas transações concorrentes que leem e modificam o mesmo pedido
    // podem sobrescrever silenciosamente a mudança uma da outra (lost update).

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItemEntity> items;
}
```

---

### Problema 5 — Decremento de inventário sem lock (race condition)

**Arquivo:** `InventoryRepository.java`

```java
public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {

    Optional<InventoryEntity> findByProductId(String productId);

    // PROBLEMA: sem @Lock, múltiplas transações podem ler o mesmo registro
    // de inventário simultaneamente. Cada uma lê quantity = 1, cada uma grava
    // quantity = 0. O resultado é quantity = 0 com múltiplos pedidos confirmados
    // para o mesmo item — overselling.
}
```

**Arquivo:** `CreateOrderUseCase.java` (trecho de decremento)

```java
// PROBLEMA: leitura e escrita sem proteção de concorrência
InventoryEntity inv = inventoryRepository.findByProductId(productId).orElseThrow();
if (inv.getQuantity() < quantity) {
    throw new OutOfStockException(productId);
}
// race condition: outra thread pode passar pela verificação acima ao mesmo tempo
inv.setQuantity(inv.getQuantity() - quantity);
inventoryRepository.save(inv);
```

---

### Problema 6 — Dual write sem garantia de atomicidade

**Arquivo:** `CreateOrderUseCase.java` (trecho de indexação)

```java
// PROBLEMA: o pedido é gravado no PostgreSQL e indexado no Elasticsearch
// em operações separadas, sem transação distribuída entre os dois.
// Se o Elasticsearch estiver fora do ar quando o PostgreSQL confirmar,
// o pedido existirá na fonte de verdade mas não no índice de busca.
// A busca retornará vazio para um pedido que existe — inconsistência silenciosa.
orderRepository.save(entity);                    // PostgreSQL — confirmado
searchRepository.save(OrderSearchDocument.from(entity));  // Elasticsearch — pode falhar
```

---

### Problema 7 — Busca full-text ineficiente via SQL LIKE

**Arquivo:** `OrderController.java` (endpoint de busca)

```java
// PROBLEMA: LIKE '%tênis%' força um full table scan no PostgreSQL.
// Sem índice de texto, cada busca percorre todos os registros da tabela.
// Com 1 milhão de pedidos, a busca degrada linearmente.
// Além disso, LIKE não faz stemming: 'tênis' não encontra 'tenis' ou 'Tênis'.
@GetMapping("/search")
public List<OrderResponse> search(@RequestParam String q) {
    return orderRepository.findByProductDescriptionContaining(q)
        .stream().map(OrderResponse::from).toList();
}
```

---

## Branch `feature/bloco-1` — soluções do Bloco 1

Deve ser criada a partir de `main`. Corrige os **Problemas 1, 2 e 3**.

### Solução 1 — Atomicidade com `@Transactional`

**Arquivo:** `CreateOrderUseCase.java`

```java
@Service
public class CreateOrderUseCase {

    // SOLUÇÃO (Bloco 1 — ACID): @Transactional envolve todo o método em uma única
    // transação do banco de dados. Se qualquer operação dentro do método falhar,
    // o banco reverte automaticamente todas as mudanças já feitas (ROLLBACK).
    // Pedido e decremento de inventário ocorrem juntos ou não ocorrem — nunca metade.
    @Transactional
    public Order execute(CreateOrderRequest request) {
        Order order = Order.create(request);
        orderRepository.save(OrderEntity.from(order));

        // agora, se esta linha falhar, o pedido acima também é revertido
        inventoryRepository.decrement(request.productId(), request.quantity());

        return order;
    }
}
```

### Solução 2 — Idempotência distribuída com Filter + Redis

**Arquivos:** `IdempotencyFilter.java` (novo) + `OrderController.java` (simplificado)

```java
// SOLUÇÃO (Bloco 1 — cross-cutting concern + CAP / CP): idempotência é uma
// responsabilidade transversal — não pertence ao domínio nem ao controlador.
// Extrair para um Filter mantém o controlador focado em orquestração de negócio.
// O Filter intercepta todas as requisições POST antes de chegarem ao controlador,
// verifica o Redis e devolve a resposta cacheada sem sequer chamar o use case.
// Redis com SET NX garante atomicidade entre pods (CP): apenas um pod registra
// a chave, mesmo que múltiplos recebam a mesma Idempotency-Key simultaneamente.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotencyFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    public IdempotencyFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        String key = request.getHeader("Idempotency-Key");

        // sem chave ou não é POST: passa sem interferência
        if (key == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // SOLUÇÃO: verifica se já existe resposta para esta chave no Redis
        // se existir, devolve sem chamar o controlador — operação idempotente
        String cached = redisTemplate.opsForValue().get("idempotency:response:" + key);
        if (cached != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(cached);
            return;
        }

        // SOLUÇÃO (CAP / CP): SET NX é atômico no Redis.
        // Se outro pod já registrou esta chave, registered = false e retorna 409.
        // O sistema prefere recusar a processar duas vezes (consistência > disponibilidade).
        Boolean registered = redisTemplate.opsForValue()
            .setIfAbsent("idempotency:lock:" + key, "processing", Duration.ofMinutes(5));

        if (Boolean.FALSE.equals(registered)) {
            // outra instância está processando esta chave agora
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return;
        }

        // captura o body da resposta para armazenar no Redis
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapped);

        // armazena apenas respostas bem-sucedidas (2xx)
        if (wrapped.getStatus() / 100 == 2) {
            String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
            redisTemplate.opsForValue().set(
                "idempotency:response:" + key,
                body,
                Duration.ofHours(24)
            );
        }

        wrapped.copyBodyToResponse();
    }
}
```

```java
// SOLUÇÃO: controlador sem qualquer lógica de idempotência —
// essa responsabilidade foi completamente delegada ao Filter.
// O método conhece apenas o domínio: recebe o request, executa o use case, devolve a resposta.
@PostMapping
public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request) {
    Order order = createOrderUseCase.execute(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
}
```

### Solução 3 — Pool de conexões com backpressure explícito

**Arquivo:** `application.yml`

```yaml
spring:
  datasource:
    hikari:
      # SOLUÇÃO (Bloco 1 — backpressure no banco): limitar o pool força a aplicação
      # a sinalizar ao cliente (via timeout) quando o banco está sob pressão,
      # em vez de acumular requisições em fila ilimitada na memória.
      # A regra prática para PostgreSQL: pool = (núcleos do banco * 2) + spindles de disco.
      # Para 4 núcleos: pool de ~10 conexões. PgBouncer escala isso sem abrir mais conexões reais.
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 3000    # falha rápido após 3s em vez de acumular silenciosamente
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    # SOLUÇÃO (Bloco 1 — observabilidade): habilitar log e estatísticas torna visível
    # quantas queries o Hibernate gera por requisição. Sem isso, o problema N+1
    # permanece invisível até a produção entrar em colapso sob carga.
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
```

---

## Branch `feature/bloco-2` — soluções do Bloco 2

Deve ser criada a partir de `feature/bloco-1`. Corrige os **Problemas 4 e 5**.

### Solução 4a — Eliminação do N+1 com JOIN FETCH

**Arquivo:** `OrderRepository.java`

```java
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    // SOLUÇÃO (Bloco 2 — N+1): JOIN FETCH instrui o Hibernate a buscar
    // os itens junto com o pedido em uma única query SQL (INNER JOIN).
    // Sem este JOIN FETCH, o Hibernate emite 1 query por pedido para carregar
    // os itens — o problema N+1. Com 100 pedidos: 101 queries → 1 query.
    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.items WHERE o.customerId = :customerId")
    List<OrderEntity> findWithItemsByCustomer(@Param("customerId") String customerId);
}
```

### Solução 4b — Batch insert com SEQUENCE

**Arquivo:** `OrderEntity.java`

```java
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    // SOLUÇÃO (Bloco 2 — batch insert): SEQUENCE com allocationSize pré-aloca
    // blocos de IDs na memória da aplicação. O Hibernate não precisa de um round-trip
    // ao banco para obter o ID após cada INSERT — ele já tem os próximos 50 IDs reservados.
    // Combinado com hibernate.jdbc.batch_size=50, os inserts são agrupados em lotes,
    // reduzindo 500 round-trips para ~10 (1 por bloco de 50 IDs).
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "orders_id_seq", allocationSize = 50)
    private Long id;

    private String customerId;
    private String status;
    private BigDecimal totalAmount;
    private Instant createdAt;

    // SOLUÇÃO (Bloco 2 — controle de versão otimista): @Version adiciona uma coluna
    // de versão na tabela. O Hibernate inclui automaticamente a verificação de versão
    // no UPDATE: "WHERE id = ? AND version = ?". Se outra transação já atualizou o registro
    // (incrementou a versão), o UPDATE retorna 0 linhas e o Hibernate lança
    // OptimisticLockException — sinalizando o conflito de forma controlada.
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OrderItemEntity> items;
}
```

**Arquivo:** `application.yml` (adições ao bloco anterior)

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          # SOLUÇÃO (Bloco 2 — batch insert): agrupa até 50 statements do mesmo tipo
          # em uma única chamada ao banco. Requer SEQUENCE (não IDENTITY) na entidade.
          # Reduz o número de round-trips de N para N/batch_size.
          batch_size: 50
          order_inserts: true   # reordena inserts por tipo para maximizar o agrupamento
          order_updates: true
        default_batch_fetch_size: 50  # agrupa carregamento lazy em queries IN() de até 50 IDs
```

### Solução 5 — Decremento de inventário com pessimistic lock

**Arquivo:** `InventoryRepository.java`

```java
public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {

    // SOLUÇÃO (Bloco 2 — pessimistic locking): PESSIMISTIC_WRITE gera
    // "SELECT ... FOR UPDATE" no banco. A linha fica bloqueada para outras transações
    // até o COMMIT ou ROLLBACK desta. Em inventário, conflito é esperado
    // (múltiplos pedidos simultâneos para o mesmo produto), então bloquear
    // na leitura é mais eficiente do que detectar conflito no update (optimistic).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryEntity i WHERE i.productId = :productId")
    Optional<InventoryEntity> findByProductIdWithLock(@Param("productId") String productId);

    Optional<InventoryEntity> findByProductId(String productId);
}
```

**Arquivo:** `CreateOrderUseCase.java` (trecho de decremento)

```java
// SOLUÇÃO (Bloco 2 — race condition): usar findByProductIdWithLock em vez de
// findByProductId serializa as transações concorrentes neste produto específico.
// A segunda transação que tentar decrementar o mesmo produto ficará bloqueada
// até a primeira confirmar — garantindo que a verificação de quantidade e
// o decremento aconteçam como uma unidade atômica.
InventoryEntity inv = inventoryRepository
    .findByProductIdWithLock(request.productId())
    .orElseThrow(() -> new OutOfStockException(request.productId()));

if (inv.getQuantity() < request.quantity()) {
    throw new OutOfStockException(request.productId());
}
inv.setQuantity(inv.getQuantity() - request.quantity());
// o save é desnecessário aqui (dirty checking do JPA faz o UPDATE),
// mas deixado explícito para clareza didática
inventoryRepository.save(inv);
```

---

## Branch `feature/bloco-3` — soluções do Bloco 3

Deve ser criada a partir de `feature/bloco-2`. Corrige os **Problemas 6 e 7** e adiciona o Outbox e o catálogo MongoDB.

### Solução 6 — Outbox pattern substituindo dual write

**Arquivo:** `OutboxEntry.java` (entidade JPA)

```java
@Entity
@Table(name = "order_outbox")
public class OutboxEntry {

    @Id
    private UUID id;
    private String aggregateId;   // ID do pedido
    private String eventType;     // ex: "ORDER_CREATED"

    @Column(columnDefinition = "jsonb")
    private String payload;       // snapshot do documento a indexar

    private Instant createdAt;
    private Instant processedAt;  // null enquanto pendente

    // SOLUÇÃO (Bloco 3 — Outbox pattern): esta entidade é gravada
    // dentro da MESMA transação do PostgreSQL que grava o pedido.
    // Se a transação confirmar, o evento está garantido no banco —
    // independente do estado do Elasticsearch.
    // Um processo separado (OutboxProcessor) lê os eventos pendentes
    // e indexa no Elasticsearch com retry automático.
    // Garante consistência eventual sem risco de perda.
}
```

**Arquivo:** `CreateOrderUseCase.java` (trecho corrigido)

```java
@Transactional
public Order execute(CreateOrderRequest request) {
    Order order = Order.create(request);
    OrderEntity saved = orderRepository.save(OrderEntity.from(order));

    // SOLUÇÃO (Bloco 3 — Outbox): em vez de chamar Elasticsearch diretamente,
    // grava um evento na tabela outbox dentro da mesma transação do Postgres.
    // Se o Elasticsearch estiver fora do ar, o pedido é salvo normalmente
    // e o evento fica na fila para ser processado quando o serviço voltar.
    // Não há mais risco de estado inconsistente entre os dois sistemas.
    OutboxEntry event = new OutboxEntry(
        UUID.randomUUID(),
        saved.getId(),
        "ORDER_CREATED",
        OrderSearchDocument.from(saved).toJson(),
        Instant.now(),
        null  // não processado ainda
    );
    outboxRepository.save(event);

    return order;
}
```

**Arquivo:** `OutboxRepository.java`

```java
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    // SOLUÇÃO (Bloco 3 — concorrência entre pods): FOR UPDATE SKIP LOCKED é nativo
    // do PostgreSQL. Quando múltiplos pods executam o @Scheduled simultaneamente:
    // - Pod A adquire lock exclusivo nas entradas 1-10 e as processa
    // - Pod B tenta as mesmas entradas: SKIP LOCKED as ignora e avança para 11-20
    // Resultado: paralelismo real sem duplicação, sem deadlock, sem coordenação externa.
    // A alternativa (SELECT sem lock) causaria reprocessamento duplicado:
    // ambos os pods leriam as mesmas entradas pendentes e indexariam duas vezes.
    @Query(value = """
        SELECT * FROM order_outbox
        WHERE processed_at IS NULL
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEntry> findUnprocessedForUpdate(@Param("limit") int limit);
}
```

**Arquivo:** `OutboxProcessor.java`

```java
@Component
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final OrderSearchRepository searchRepository;

    // SOLUÇÃO (Bloco 3 — Outbox processor com controle de concorrência):
    // findUnprocessedForUpdate usa FOR UPDATE SKIP LOCKED — cada entrada
    // é processada por exatamente um pod, mesmo com múltiplas instâncias ativas.
    // Se o Elasticsearch falhar, a transação faz rollback: o lock é liberado
    // e a entrada permanece pendente para o próximo ciclo de qualquer pod.
    // Não há perda de dado: a entrada só é marcada como processada após
    // a indexação confirmar com sucesso E a transação confirmar no Postgres.
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void process() {
        // busca até 10 entradas com lock exclusivo, pulando as já bloqueadas
        List<OutboxEntry> pending = outboxRepository.findUnprocessedForUpdate(10);
        for (OutboxEntry entry : pending) {
            try {
                searchRepository.save(
                    OrderSearchDocument.fromJson(entry.getPayload())
                );
                entry.setProcessedAt(Instant.now());
                outboxRepository.save(entry);
            } catch (Exception e) {
                // a transação faz rollback: lock liberado, entrada volta para a fila
                // em produção: adicionar coluna retry_count e mover para dead-letter após N falhas
                throw new RuntimeException("Falha ao processar outbox entry " + entry.getId(), e);
            }
        }
    }
}
```

### Solução 7 — Busca full-text com Elasticsearch

**Arquivo:** `OrderSearchDocument.java`

```java
// SOLUÇÃO (Bloco 3 — Elasticsearch): este documento é uma projeção otimizada
// para leitura — estrutura diferente do OrderEntity, projetada para busca.
// O índice invertido do Elasticsearch encontra documentos por termo em O(1),
// independente do volume total. Ao contrário do LIKE no SQL, o analyzer
// 'portuguese' faz stemming: 'tênis', 'tenis' e 'Tênis' são equivalentes na busca.
@Document(indexName = "orders")
public class OrderSearchDocument {

    @Id
    private String id;

    // SOLUÇÃO: Text com analyzer 'portuguese' habilita busca full-text
    // com stemming, remoção de stopwords e normalização de acentos.
    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String customerName;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String productDescription;

    // SOLUÇÃO: Keyword é indexado como string exata — usado para filtros,
    // não para busca textual. Adequado para status, IDs, categorias.
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Double)
    private BigDecimal totalAmount;
}
```

**Arquivo:** `SearchOrdersUseCase.java`

```java
@Service
public class SearchOrdersUseCase {

    private final OrderSearchRepository searchRepository;

    // SOLUÇÃO (Bloco 3 — busca full-text): delega a busca ao Elasticsearch,
    // que usa índice invertido para localizar documentos por termo.
    // O banco relacional (PostgreSQL) não é consultado nesta operação —
    // o Elasticsearch é o sistema especializado para este tipo de query.
    public List<Order> search(String query) {
        return searchRepository
            .findByProductDescriptionContaining(query)
            .stream()
            .map(this::toDomain)
            .toList();
    }
}
```

### Solução — Catálogo de produtos com MongoDB

**Arquivo:** `ProductDocument.java`

```java
// SOLUÇÃO (Bloco 3 — MongoDB / schema flexível): o catálogo tem atributos
// que variam por categoria (eletrônico: voltagem; roupa: tamanho, cor; livro: ISBN).
// No modelo relacional, isso exigiria EAV (Entity-Attribute-Value) ou JSONB —
// soluções trabalhosas ou com perda de tipagem. O documento MongoDB acomoda
// estruturas variáveis nativamente, sem schema fixo.
// A fonte de verdade continua sendo o PostgreSQL para dados transacionais;
// o MongoDB serve o catálogo de leitura (read model), tolerando consistência eventual.
@Document(collection = "product_catalog")
public class ProductDocument {

    @Id
    private String id;
    private String name;
    private BigDecimal price;
    private String category;

    // atributos variam por categoria — sem schema fixo
    private Map<String, Object> attributes;
}
```

---

## docker-compose.yml (base — branch `main`)

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    container_name: meli-orders-postgres
    environment:
      POSTGRES_DB: orders_db
      POSTGRES_USER: orders
      POSTGRES_PASSWORD: orders
    # DEMO WAL (Bloco 2): habilita WAL no nível de replicação e cria o usuário
    # de replicação usado pela réplica. Sem wal_level=replica, pg_stat_replication
    # não exibe nenhuma linha e o demo de lag_bytes não funciona.
    command: >
      postgres
        -c wal_level=replica
        -c max_wal_senders=3
        -c wal_keep_size=64
        -c synchronous_commit=off
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/init-replication.sql:/docker-entrypoint-initdb.d/init-replication.sql:ro
    ports:
      - "5432:5432"

  # DEMO WAL (Bloco 2): réplica de streaming do primário.
  # Ativa apenas com: docker compose --profile wal-demo up
  # Após subir, use no primário:
  #   SELECT client_addr, pg_wal_lsn_diff(sent_lsn, replay_lsn) AS lag_bytes
  #   FROM pg_stat_replication;
  # Para simular falha: docker stop orders-postgres-replica
  # Para observar recuperação: docker start orders-postgres-replica
  postgres-replica:
    image: postgres:16-alpine
    container_name: meli-orders-postgres-replica
    environment:
      PGUSER: replicator
      PGPASSWORD: replicator
    command: |
      bash -c "
        until pg_basebackup -h postgres -D /var/lib/postgresql/data \
          -U replicator -Fp -Xs -P -R 2>/dev/null; do
          echo 'Aguardando primary...' && sleep 3
        done && postgres
      "
    depends_on:
      - postgres
    ports:
      - "5433:5432"
    profiles:
      - wal-demo

  mongodb:
    image: mongo:7
    container_name: meli-orders-mongodb
    ports:
      - "27017:27017"

  elasticsearch:
    image: elasticsearch:8.13.0
    container_name: meli-orders-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"

  redis:
    image: redis:7-alpine
    container_name: meli-orders-redis
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

---

## Arquivo auxiliar — `docker/init-replication.sql`

Crie este arquivo no repositório para que o container do primário crie o usuário de replicação automaticamente na inicialização:

```sql
-- Executado pelo postgres primário no primeiro boot (docker-entrypoint-initdb.d)
-- Cria o usuário de replicação usado pela réplica para conectar via streaming WAL.
-- Sem este usuário, o pg_basebackup na réplica falha com "authentication failed".
CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator';

-- Permite que a réplica se autentique como replicator
-- (equivalente a adicionar linha no pg_hba.conf, mas via SQL é mais portátil em Docker)
SELECT pg_reload_conf();
```

## Teste de performance com Gatling

O objetivo da simulação é **tornar visível o impacto de cada conjunto de soluções** — os números mudam a cada branch e servem de evidência durante a aula.

### Arquivo: `src/test/java/br/com/meli/orders/performance/OrderSimulation.java`

```java
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
 * Ou diretamente pela JVM (sem Maven):
 *   mvn test-compile && \
 *   mvn gatling:test -Dgatling.simulationClass=br.com.meli.orders.performance.OrderSimulation
 *
 * O relatório HTML é gerado em target/gatling/ordersimulation-<timestamp>/index.html
 */
public class OrderSimulation extends Simulation {

    // URL base — mude para o IP/porta correta se rodar fora do localhost
    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        // desabilita keep-alive para simular clientes independentes
        .disableKeepAlive();

    // -----------------------------------------------------------------------
    // Cenário 1 — criação de pedidos (exercita ACID, pool, batch, Outbox)
    // -----------------------------------------------------------------------
    ScenarioBuilder createOrders = scenario("Criar Pedidos")
        .exec(
            http("POST /orders")
                .post("/orders")
                // Idempotency-Key única por request — evita 409 do IdempotencyFilter
                // (a partir de feature/bloco-1; no main este header é ignorado)
                .header("Idempotency-Key", session -> UUID.randomUUID().toString())
                .body(StringBody("""
                    {
                      "customerId": "customer-alice",
                      "items": [
                        { "productId": "prod-tenis", "quantity": 1 }
                      ]
                    }
                    """))
                .check(status().in(201, 200, 409))
                // 409 é esperado se o estoque esgota (OutOfStock) — não é falha do teste
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
    // Intencionalmente conservador para caber em máquinas de aluno (8 GB RAM)
    // e ainda assim revelar diferença entre branches.
    // -----------------------------------------------------------------------
    {
        setUp(
            createOrders
                .injectOpen(
                    rampUsers(20).during(Duration.ofSeconds(10)),  // aquecimento
                    constantUsersPerSec(10).during(Duration.ofSeconds(30)) // carga
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
```

### Como usar em aula

1. Suba a aplicação e o docker-compose: `docker compose up -d && mvn spring-boot:run`
2. Execute o teste na branch atual: `mvn gatling:test`
3. Abra o relatório em `target/gatling/ordersimulation-*/index.html`
4. Registre o **p50, p95 e taxa de erro** de cada cenário na tabela abaixo
5. Faça checkout na próxima branch, reinicie a aplicação e repita

### Resultados esperados por branch

| Branch | POST /orders p95 | GET /orders p95 | GET /search p95 | Erros |
|---|---|---|---|---|
| `main` | > 800 ms | > 1 s (N+1) | > 2 s (LIKE) | > 5 % (pool esgotado) |
| `feature/bloco-1` | < 400 ms | > 1 s (N+1 ainda) | > 2 s | < 1 % |
| `feature/bloco-2` | < 300 ms | < 200 ms | > 2 s (ainda LIKE) | < 1 % |
| `feature/bloco-3` | < 300 ms | < 200 ms | < 150 ms (ES) | < 1 % |

> Os valores acima são estimativas para hardware de desenvolvimento (8 GB RAM, banco local).  
> O que importa para a aula é a **ordem de grandeza e a tendência** — cada branch deve ser visivelmente melhor que a anterior.

---

## Instruções de inicialização de branches

```bash
# branch base — todos os problemas plantados
git checkout -b main
git add .
git commit -m "feat: projeto base com problemas de persistência para a aula 3"

# bloco 1 — ACID, IdempotencyFilter + Redis, backpressure do pool
git checkout -b feature/bloco-1
# aplique as soluções 1, 2 e 3 sobre o código base
git add .
git commit -m "feat(bloco-1): @Transactional, IdempotencyFilter com Redis SET NX e HikariCP configurado"

# bloco 2 — N+1, batch insert, optimistic/pessimistic locking
git checkout -b feature/bloco-2
# aplique as soluções 4a, 4b e 5 sobre feature/bloco-1
git add .
git commit -m "feat(bloco-2): JOIN FETCH, SEQUENCE + batch_size, @Version e pessimistic lock no inventário"

# bloco 3 — Outbox + SKIP LOCKED, Elasticsearch, MongoDB
git checkout -b feature/bloco-3
# aplique as soluções 6, 7 e o catálogo MongoDB sobre feature/bloco-2
git add .
git commit -m "feat(bloco-3): Outbox com FOR UPDATE SKIP LOCKED, busca full-text com Elasticsearch, catálogo MongoDB"

# demo WAL (opcional — usar durante o Bloco 2 para demonstrar replication_lag_bytes)
docker compose --profile wal-demo up -d
# depois de subir:
# docker exec -it <container-postgres> psql -U orders -c "SELECT * FROM pg_stat_replication;"
# para simular falha da réplica:
# docker stop <container-postgres-replica>
```

---

## Estudo de Caso — Hands-on: Postgres + WAL & Spike NoSQL

O projeto deve suportar o roteiro do estudo de caso que será executado ao final da aula com a branch `feature/bloco-3` ativa (todos os problemas resolvidos) e o docker-compose com o profile `wal-demo`.

Os quatro passos do estudo de caso e o que cada um exige do projeto gerado:

### Passo 1 — Batch insert com Gatling

Executar `mvn gatling:test` na branch `feature/bloco-3` e comparar o p95 do cenário `createOrders` com o resultado da branch `main`.

Pré-requisito no projeto: `OrderSimulation.java` já descrito na seção de Gatling acima.  
Pré-requisito no `application.yml` (feature/bloco-2): `batch_size: 50`, `order_inserts: true`, gerador `SEQUENCE` com `allocationSize=50`.

### Passo 2 — Análise de WAL

Usar `pg_waldump` dentro do container `meli-orders-postgres` para observar o volume de WAL por segundo durante carga.

Pré-requisito: o container Postgres deve se chamar `meli-orders-postgres` e o primário deve estar configurado com `wal_level=replica` (já configurado no docker-compose desta spec).  
Comandos de referência:
```bash
docker exec -it meli-orders-postgres bash
pg_waldump -p /var/lib/postgresql/data/pg_wal -f <arquivo_wal_atual> | grep -E "INSERT|UPDATE" | head -40
```

### Passo 3 — Chaos test: sync vs async replication

Derrubar e subir o container `meli-orders-postgres-replica` enquanto carga corre.  
O container da réplica deve se chamar `meli-orders-postgres-replica`.  
A configuração `synchronous_commit=off` no primário garante que o comportamento AP seja observável.

### Passo 4 — Spike NoSQL: MongoDB + Elasticsearch + Outbox

Parar `meli-orders-elasticsearch`, criar pedidos, subir de volta e observar o Outbox drenar.  
O container MongoDB deve se chamar `meli-orders-mongodb`.

O projeto deve ter:
- `OutboxProcessor` com `@Scheduled(fixedDelay=1000)` e `FOR UPDATE SKIP LOCKED` (já descrito)
- Uma coleção MongoDB `order_events` onde `CreateOrderUseCase` salva o evento `OrderPlaced` (além da `OutboxEntry` JPA)

**Estrutura do documento de evento MongoDB:**

```java
// Arquivo: infrastructure/mongo/OrderEventDocument.java
// Salvo no mesmo @Transactional de CreateOrderUseCase (via MongoTemplate ou repositório)
// NÃO é uma fonte de verdade — é um log de eventos para auditoria e replay.
// A consistência eventual é aceitável: se o save no Mongo falhar após o commit
// no Postgres, o OutboxProcessor reprocessa e o evento é re-publicado.
@Document(collection = "order_events")
public class OrderEventDocument {

    @Id
    private String id;              // UUID em String
    private String orderId;
    private String customerId;
    private String eventType;       // "OrderPlaced"
    private Instant occurredAt;
    private Map<String, Object> payload; // snapshot do pedido no momento do evento
}
```

---

## Nomes dos containers (docker-compose)

Os nomes abaixo são usados nos comandos do estudo de caso — o `docker-compose.yml` deve definir `container_name` explícito para cada serviço:

| Serviço | `container_name` |
|---|---|
| PostgreSQL primário | `meli-orders-postgres` |
| PostgreSQL réplica | `meli-orders-postgres-replica` |
| MongoDB | `meli-orders-mongodb` |
| Elasticsearch | `meli-orders-elasticsearch` |
| Redis | `meli-orders-redis` |

---

## Comportamento esperado ao final de cada branch

| Branch | O que funciona | O que ainda falha |
|---|---|---|
| `main` | Aplicação sobe, endpoints respondem | ACID, idempotência multi-pod, N+1, overselling, dual write |
| `feature/bloco-1` | Atomicidade, idempotência distribuída, pool com backpressure | N+1, overselling, dual write |
| `feature/bloco-2` | + Queries otimizadas, batch insert, sem overselling | Dual write, busca full-text ineficiente |
| `feature/bloco-3` | Tudo funcionando com Outbox e Elasticsearch | — |
