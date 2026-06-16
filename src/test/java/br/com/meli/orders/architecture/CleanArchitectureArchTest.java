package br.com.meli.orders.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SOLUÇÃO: testes de arquitetura validando a Dependency Rule da Clean Architecture.
 * "O codigo fonte de uma camada interna nao pode mencionar nada
 *  sobre uma camada externa" — Robert C. Martin.
 * Dependencias sempre apontam de fora para dentro (infra -> app -> domain).
 */
@Tag("architecture")
class CleanArchitectureArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
                .importPackages("br.com.meli.orders");
    }

    @Test
    void domainMustNotDependOnSpringFramework() {
        // SOLUÇÃO: o dominio e um POJO puro — sem anotacoes ou tipos do Spring.
        // Isso garante que o modelo de negocio pode ser testado e reutilizado
        // independentemente de qualquer framework.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("O dominio nao deve depender do Spring Framework. " +
                         "Use POJOs puros no dominio e configure beans na camada de infraestrutura.");

        rule.check(classes);
    }

    @Test
    void domainMustNotDependOnInfrastructure() {
        // SOLUÇÃO: dominio nao conhece detalhes de persistencia, mensageria ou frameworks.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("O dominio nao deve depender da infraestrutura. " +
                         "Inverta as dependencias usando portas (interfaces) de saida.");

        rule.check(classes);
    }

    @Test
    void domainMustNotDependOnApi() {
        // SOLUÇÃO: o dominio nao conhece controllers REST, DTOs de request/response ou HTTP.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..api..")
                .because("O dominio nao deve depender da camada de API (REST). " +
                         "Controllers sao adapters de entrada que convertem HTTP para casos de uso.");

        rule.check(classes);
    }

    @Test
    void applicationMustNotDependOnApi() {
        // SOLUÇÃO: casos de uso nao sabem como foram invocados (REST, gRPC, mensagem, etc.)
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .and().resideOutsideOfPackage("..application.acl..")
                .should().dependOnClassesThat()
                .resideInAPackage("..api.dto..")
                .because("Casos de uso nao devem depender de DTOs da camada de API. " +
                         "Use tipos de dominio ou comandos especificos como parametros dos casos de uso.");

        rule.check(classes);
    }

    @Test
    void infrastructureMustNotBeAccessedByDomain() {
        // SOLUÇÃO: nenhuma classe de dominio importa de infraestrutura.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("jakarta.persistence..")
                .because("Anotacoes JPA (@Entity, @Column, etc.) nao pertencem ao dominio. " +
                         "Use entidades JPA separadas (OrderEntity) na camada de infraestrutura.");

        rule.check(classes);
    }
}

