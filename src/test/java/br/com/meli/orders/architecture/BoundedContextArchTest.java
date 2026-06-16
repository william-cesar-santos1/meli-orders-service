package br.com.meli.orders.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Testes ArchUnit validando o isolamento entre Bounded Contexts.
 * Principio: Architecture as Code — regras de design testadas automaticamente.
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
    void orderDomainMustNotDependOnBillingDomain() {
        // O domínio de order não importa tipos do domínio de billing diretamente.
        // A comunicação ocorre via ACL em billing.application.acl.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..billing.domain..")
                .because("O contexto de Order não deve depender diretamente do contexto de Billing. " +
                         "Use o BillingPaymentTranslator (ACL) e BillingPort para comunicação.");
        rule.check(classes);
    }

    @Test
    void aclIsTheOnlyBridgeBetweenOrderApplicationAndBillingDomain() {
        // Fora do ACL, nenhuma classe de order.application pode depender de billing.domain
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .and().resideOutsideOfPackage("..billing.application.acl..")
                .should().dependOnClassesThat()
                .resideInAPackage("..billing.domain..")
                .because("Apenas a Anti-Corruption Layer (billing.application.acl) deve conhecer " +
                         "tipos do contexto de Billing. Use BillingPaymentTranslator para traduções.");
        rule.check(classes);
    }

    @Test
    void orderDomainMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("O domínio não deve conhecer detalhes de infraestrutura. " +
                         "Use portas de saída (ports/out) para inverter a dependência.");
        rule.check(classes);
    }

    @Test
    void orderApplicationMustCommunicateWithBillingOnlyThroughPort() {
        // order.application.saga só pode importar BillingPort (interface), não BillingHttpAdapter
        ArchRule rule = noClasses()
                .that().resideInAPackage("..order.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..billing.infrastructure..")
                .because("Casos de uso do contexto de Order devem depender apenas de BillingPort " +
                         "(interface em billing.application.port.out), nunca de BillingHttpAdapter.");
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
