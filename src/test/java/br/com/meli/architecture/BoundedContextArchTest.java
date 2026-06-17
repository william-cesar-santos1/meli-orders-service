package br.com.meli.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Testes ArchUnit validando o isolamento entre camadas e contextos.
 *
 * O contexto de Billing foi externalizado como microserviço separado (simulado via WireMock).
 * A comunicação com o Billing externo ocorre exclusivamente via:
 *   - BillingPort (porta de saída em order.application.port.out)
 *   - BillingPaymentTranslator (ACL em order.application.acl)
 *   - BillingHttpAdapter (adapter HTTP em order.infrastructure.billing)
 *
 * Princípio: Architecture as Code — regras de design testadas automaticamente.
 */
@Tag("architecture")
class BoundedContextArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        // Importa apenas classes de produção — exclui testes de integração
        classes = new ClassFileImporter()
                .importPath("target/classes");
    }

    @Test
    void orderDomainMustNotDependOnInfrastructure() {
        // O domínio é o núcleo — não pode conhecer detalhes de infraestrutura.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("O domínio não deve conhecer detalhes de infraestrutura. " +
                         "Use portas de saída (ports/out) para inverter a dependência.");
        rule.check(classes);
    }

    @Test
    void orderDomainMustNotDependOnAcl() {
        // O domínio não deve conhecer tipos ACL de comunicação com serviços externos.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..order.application.acl..")
                .because("O domínio de Order não deve depender de tipos ACL. " +
                         "A tradução ocorre na camada de application via BillingPaymentTranslator.");
        rule.check(classes);
    }

    @Test
    void orderApplicationMustNotDependOnBillingInfrastructure() {
        // A camada de application só conhece a interface BillingPort, nunca o adapter HTTP.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..order.infrastructure.billing..")
                .because("Casos de uso devem depender apenas de BillingPort (interface), " +
                         "nunca de BillingHttpAdapter. Princípio: Dependency Inversion.");
        rule.check(classes);
    }

    @Test
    void orderDomainMustNotDependOnApplicationPorts() {
        // O domínio puro não depende nem das portas de saída.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..order.application.port..")
                .because("O domínio não deve conhecer portas de saída. " +
                         "Portas são contratos da camada de application.");
        rule.check(classes);
    }

    @Test
    void useCasesMustNotHaveSpringAnnotations() {
        // Use cases são POJOs puros — Spring não entra na camada de application
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .and().resideOutsideOfPackage("..order.application.port..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework.stereotype..")
                .because("Casos de uso são POJOs puros. Use @Bean em UseCaseConfig para registrá-los.");
        rule.check(classes);
    }
}
