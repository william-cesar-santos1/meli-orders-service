# Prompt — Aula 5: Microsserviços, DDD & Clean Architecture

Você é um agente de código responsável por gerar o projeto incremental da aula 5 no repositório local:

- Caminho do projeto: `/Users/william/classes/lets_code/meli-1699/meli-orders-service`
- Linguagem e stack: Java 21 + Spring Boot 3
- Continuidade: projeto existente (não iniciar projeto novo)
- Modelo de aula: cada tópico com exercício prático

Objetivo: preparar o estado de problema em `feature/ms-base` (criada a partir de `feature/load-testing-6`) e implementar as soluções por tópico em branches de feature, com commits locais e sem push.

Além dos objetivos de arquitetura, a aula 5 deve **remover o MongoDB do projeto** (código, dependências e infraestrutura), mantendo build e execução locais funcionais.

---

## Regras obrigatórias

1. Não alterar `conteudo-proposto.md`.
2. Código sempre em inglês (nomes de classes, métodos, variáveis, pacotes, arquivos).
3. Comentários explicativos sempre em português BR.
4. Em código de problema, usar comentários `// PROBLEMA: ...` explicando princípio de engenharia.
5. Em código de solução, usar comentários `// SOLUÇÃO: ...` explicando princípio de engenharia.
6. Evitar over-engineering: mínimo necessário para demonstrar conceito com boa prática.
7. Respeitar camadas: domain -> application -> infrastructure -> api.
8. Se houver infra externa, manter `docker-compose.yml` simples e executável.
9. Remover completamente MongoDB do projeto durante a aula 5:
    - Remover dependências de Mongo do build (`spring-boot-starter-data-mongodb` e correlatas)
    - Remover adapters/repositórios/documentos Mongo não utilizados no novo desenho
    - Remover configurações `spring.data.mongodb.*` e perfis associados
    - Remover serviço `mongo` do `docker-compose.yml`
    - Garantir que nenhum teste dependa de Mongo após a refatoração
9. Após cada conjunto de mudanças, validar executabilidade:
    - Build: `mvn package -q`
    - Infra: `docker compose up -d` + `docker compose ps`
    - Encerrar infra: `docker compose down`
10. Commitar em cada branch; nunca executar `git push`.

11. Incluir Arch Tests (ArchUnit) a partir das branches de solução:
- Dependência de teste no build (se ainda não existir):
    - `com.tngtech.archunit:archunit-junit5`
- Os testes devem rodar em `mvn test` e validar regras arquiteturais da aula.

---

## Parte A — Estado de Problema (branch `feature/ms-base`)

Faça checkout em `feature/load-testing-6`, crie a branch `feature/ms-base` a partir dela e plante os problemas lógicos (sem quebrar o build):

### Arquitetura propositalmente problemática

1. Misturar modelo de Billing dentro de Order:
    - Arquivo alvo: `src/main/java/.../domain/order/Order.java`
    - Introduzir dependência de tipo de pagamento externo (`PaymentStatus`) dentro de `Order`.
    - Comentar com `// PROBLEMA:` sobre vazamento de contexto.

2. Misturar regra de negócio com framework:
    - Arquivo alvo: `src/main/java/.../application/CreateOrderUseCase.java`
    - Colocar anotação `@Service` e uso direto de `SpringDataOrderRepository` no mesmo caso de uso.
    - Comentar com `// PROBLEMA:` sobre domínio acoplado a infraestrutura.

3. Fluxo distribuído sem compensação:
    - Arquivo alvo: `src/main/java/.../application/PlaceOrderAndChargeUseCase.java`
    - Fluxo linear: cria pedido -> cobra pagamento -> marca pago, sem fallback/compensação.
    - Comentar com `// PROBLEMA:` sobre inconsistência em falhas parciais.

4. Gateway sem proteção mínima:
    - Arquivo alvo: `src/main/java/.../api/BillingClient.java`
    - Sem timeout explícito e sem circuit breaker.
    - Comentar com `// PROBLEMA:` sobre falha em cascata.

5. Presença de stack legado não necessária:
    - Manter o MongoDB ainda configurado no estado-base para gerar débito técnico realista.
    - Comentar com `// PROBLEMA:` sobre custo operacional e acoplamento de infraestrutura sem necessidade de domínio.

### Testes no estado problema

1. Garantir que o projeto ainda compila. Os testes novos podem ficar quebrados — isso é intencional para o exercício.
2. Adicionar ao menos um teste de integração que exponha o cenário de pedido órfão (sem compensação). O teste pode falhar neste branch; ele será corrigido nas branches de solução.

### Commit em `feature/ms-base`

- Mensagem: `aula-5: base — problemas de acoplamento e fluxo distribuido`

---

## Parte B — Soluções por tópico (branches encadeadas)

Importante: usar encadeamento para reduzir conflitos.

Ordem:
`feature/ms-base` -> `feature/bounded-contexts-7` -> `feature/clean-architecture-8` -> `feature/saga-resilience-9`

### Bloco 1 — `feature/bounded-contexts-7`

Crie a branch a partir de `feature/ms-base` e implemente:

1. DDD tático:
    - Criar/ajustar `Entities`, `Value Objects` e `Aggregate Root` em `domain/order`.
    - Remover dependência direta de tipos de `billing` dentro de `order`.

2. Bounded Contexts explícitos:
    - Separar pacotes por contexto: `domain/order` e `domain/billing`.
    - Introduzir evento interno `OrderPaid` no contexto de order.

3. Remoção inicial de MongoDB:
    - Remover documentos/repositórios Mongo que não são mais usados no fluxo principal.
    - Migrar leituras/escritas necessárias para portas/adapters já definidos para storage principal da aula.

4. Anti-Corruption Layer:
    - Criar tradutor em `application/acl` para converter evento externo de billing em linguagem de order.

5. Testes:
    - Testes de domínio para invariantes de `Order`.
    - Validar ausência de import de billing no pacote de order.
    - Adicionar Arch Test para bounded contexts:
        - classes em `..domain.order..` não podem depender de `..domain.billing..`
        - exceção permitida apenas via ACL em `..application.acl..`

6. Exercício (stub para aluno):
    - Deixar TODO guiado no ACL para suportar novo estado `PARTIALLY_REFUNDED`.

Critérios de aceite:
- `order` não depende semanticamente de `billing`.
- Módulos principais não dependem de tipos Mongo.
- Arch Test de bounded contexts passando.
- Build verde.

Commit:
- `feat: bounded contexts e acl — solucao aula 5`

### Bloco 2 — `feature/clean-architecture-8`

Crie a branch a partir de `feature/bounded-contexts-7` e implemente:

1. Ports and Adapters:
    - Definir portas de saída (`SaveOrderPort`, `FindOrderPort`, `PublishEventPort`).
    - Caso de uso depende apenas de interfaces.

2. Dependency Rule:
    - Remover anotações/framework de `domain`.
    - Spring annotations apenas em adapters/config.

3. Screaming architecture:
    - Reorganizar pacotes para destacar domínio e casos de uso.

4. Adapters:
    - Adapter REST para entrada (`api`).
    - Adapter JPA para persistência (`infrastructure/jpa`).

5. Remoção definitiva de Mongo no build/config:
    - Atualizar `pom.xml` removendo dependências Mongo.
    - Limpar `application*.yml` de propriedades Mongo.
    - Garantir startup sem tentar criar conexão com Mongo.

6. Testes:
    - Unitário de caso de uso com fake port in-memory.
    - Teste de adapter JPA com Testcontainers (se já existir suporte no projeto).
    - Adicionar Arch Test de Clean Architecture (Dependency Rule):
        - `..domain..` não depende de `org.springframework..`, `..infrastructure..` ou `..api..`
        - `..application..` não depende de `..api..`
        - `..api..` não pode ser acessado por `..infrastructure..`

7. Adapter de leitura otimizada (`FindOrderSummaryPort`) — implementar completo:
    - Definir porta de saída em `application/port/out/FindOrderSummaryPort.java`:
      ```java
      // SOLUÇÃO: porta dedicada para leitura de resumo de pedido — segregada de FindOrderPort
      // para suportar projeções otimizadas sem expor o agregado completo.
      public interface FindOrderSummaryPort {
          Optional<OrderSummary> findSummaryById(OrderId orderId);
      }
      ```
    - Criar Value Object `domain/order/OrderSummary.java` com campos: `orderId`, `customerId`, `status`, `total`.
    - Implementar adapter JPA em `infrastructure/jpa/JpaOrderSummaryAdapter.java` usando projeção JPQL:
      ```java
      // SOLUÇÃO: projeção JPQL retorna apenas os campos necessários — evita carregar o agregado inteiro.
      @Query("SELECT new ...OrderSummary(o.id, o.customerId, o.status, o.total) FROM OrderJpaEntity o WHERE o.id = :id")
      Optional<OrderSummary> findSummaryById(@Param("id") UUID id);
      ```
    - Injetar `FindOrderSummaryPort` no caso de uso ou controller de consulta que precisar de leitura leve.
    - Adicionar teste unitário com fake in-memory para `FindOrderSummaryPort`.

Critérios de aceite:
- `domain` sem `org.springframework`.
- Projeto sem dependências Mongo no build.
- Arch Test de camadas passando.
- `mvn package -q` verde.

Commit:
- `feat: clean architecture com ports-adapters — solucao aula 5`

### Bloco 3 — `feature/saga-resilience-9`

Crie a branch a partir de `feature/clean-architecture-8` e implemente:

1. Saga orchestrator:
    - Criar `OrderSagaOrchestrator` para fluxo:
        - `OrderPlaced` -> `ChargeRequested` -> (`PaymentConfirmed` ou `PaymentFailed`) -> confirmar/cancelar pedido.

2. Compensação:
    - Em falha irreversível de pagamento, cancelar pedido com motivo de negócio.

3. Resiliência:
    - Aplicar `CircuitBreaker` no adapter de billing (Resilience4j).
    - Configurar timeout e retry com limites conservadores.

4. Infra local:
    - Ajustar/criar `docker-compose.yml` para dependências mínimas da aula:
        - `postgres`
        - `redpanda` (ou broker único equivalente para eventos)
            - **não incluir `mongo`**
    - Evitar serviços desnecessários.

5. Testes:
    - Integração do fluxo distribuído confirmando cenário feliz e cenário compensado.
    - Teste garantindo que breaker abre após falhas consecutivas.
    - Manter Arch Tests existentes verdes após introdução da saga.

6. Exercício (stub para aluno):
    - TODO para idempotência no consumo de evento `PaymentConfirmed`.

Critérios de aceite:
- Falha em billing não deixa pedido em estado inconsistente.
- `docker compose up -d` com serviços rodando.
- `docker compose config --services` não lista `mongo`.
- Build e testes verdes.

Commit:
- `feat: saga, circuit breaker e compensacao — solucao aula 5`

---

## Entregáveis finais esperados

1. Branch `feature/ms-base` (criada a partir de `feature/load-testing-6`) com problemas plantados e executáveis.
2. Branches de solução encadeadas:
    - `feature/bounded-contexts-7`
    - `feature/clean-architecture-8`
    - `feature/saga-resilience-9`
3. Comentários `// PROBLEMA:` e `// SOLUÇÃO:` nos pontos-chave.
4. Projeto compilando e infraestrutura local funcional em cada branch.
5. Sem push remoto.
6. MongoDB removido de dependências, configurações, código e docker-compose.
7. Suite de Arch Tests (ArchUnit) criada e verde nas branches de solução.

---

## Comandos Git esperados (sem push)

```bash
git checkout feature/load-testing-6
git checkout -b feature/ms-base
# aplicar problemas
git add . && git commit -m "aula-5: base — problemas de acoplamento e fluxo distribuido"

git checkout -b feature/bounded-contexts-7
# aplicar solução bloco 1
git add . && git commit -m "feat: bounded contexts e acl — solucao aula 5"

git checkout -b feature/clean-architecture-8
# aplicar solução bloco 2
git add . && git commit -m "feat: clean architecture com ports-adapters — solucao aula 5"

git checkout -b feature/saga-resilience-9
# aplicar solução bloco 3
git add . && git commit -m "feat: saga, circuit breaker e compensacao — solucao aula 5"
```

Validação mínima em cada branch:

```bash
mvn package -q
docker compose up -d
docker compose ps
docker compose config --services
docker compose down
```
