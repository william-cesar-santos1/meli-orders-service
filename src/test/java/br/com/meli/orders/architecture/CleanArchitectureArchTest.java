package br.com.meli.orders.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Testes ArchUnit validando a Dependency Rule da Clean Architecture.
 * "O código fonte de uma camada interna não pode mencionar nada
 *  sobre uma camada externa" — Robert C. Martin.
 *
 * Estrutura de pacotes domain-first:
 *   order/{domain, application, infrastructure, api}
 *   billing/{domain, application, infrastructure}
 *   shared/{domain, api, infrastructure}
 *
 * Nota: importa apenas classes de produção (target/classes) — testes de integração
 * legitimamente acessam infraestrutura para seed de dados e não devem ser analisados.
 */
@Tag("architecture")
class CleanArchitectureArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        // Importa apenas classes de produção — exclui testes de integração
        classes = new ClassFileImporter()
                .importPath("target/classes");
    }

    @Test
    void domainMustNotDependOnSpringFramework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .or().resideInAPackage("..billing.domain..")
                .or().resideInAPackage("..shared.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("O domínio deve ser um POJO puro — sem anotações do Spring Framework.");
        rule.check(classes);
    }

    @Test
    void domainMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .or().resideInAPackage("..billing.domain..")
                .or().resideInAPackage("..shared.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("O domínio não deve depender de infraestrutura. Use portas de saída.");
        rule.check(classes);
    }

    @Test
    void domainMustNotDependOnApi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .or().resideInAPackage("..billing.domain..")
                .or().resideInAPackage("..shared.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..order.api..")
                .orShould().dependOnClassesThat()
                .resideInAPackage("..shared.api..")
                .because("O domínio não deve conhecer controllers REST ou DTOs HTTP.");
        rule.check(classes);
    }

    @Test
    void applicationMustNotDependOnApiLayer() {
        // Casos de uso não podem depender de nada da camada api (nem DTOs, nem controllers, nem filtros)
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .and().resideOutsideOfPackage("..billing.application.acl..")
                .should().dependOnClassesThat()
                .resideInAPackage("..order.api..")
                .because("Casos de uso não devem depender da camada de API. " +
                         "O controller converte DTO (CreateOrderRequest) em Comando (PlaceOrderCommand) " +
                         "antes de invocar o use case.");
        rule.check(classes);
    }

    @Test
    void applicationMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .or().resideInAPackage("..billing.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("Casos de uso não devem depender de infraestrutura. " +
                         "Use portas de saída (ports/out) para inverter a dependência.");
        rule.check(classes);
    }

    @Test
    void infrastructureMustNotBeAccessedByDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("jakarta.persistence..")
                .because("Anotações JPA (@Entity, @Column) não pertencem ao domínio. " +
                         "Use entidades JPA separadas (OrderEntity) na camada de infraestrutura.");
        rule.check(classes);
    }
}
