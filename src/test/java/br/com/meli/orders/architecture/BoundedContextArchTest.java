package br.com.meli.orders.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SOLUÇÃO: testes de arquitetura com ArchUnit validam regras de bounded contexts.
 * Rodam em mvn test e falham o build se as regras forem violadas.
 * Principio: Architecture as Code — as regras de design sao testadas automaticamente,
 * nao apenas documentadas. Previne regressoes arquiteturais futuras.
 */
@Tag("architecture")
class BoundedContextArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
                .importPackages("br.com.meli.orders");
    }

    @Test
    void orderContextMustNotDependOnBillingContext() {
        // SOLUÇÃO: o contexto de orders (domain.order) nao deve importar diretamente
        // tipos do contexto de billing (domain.billing).
        // A comunicacao ocorre apenas via ACL em application.acl.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.order..")
                .should().dependOnClassesThat()
                .resideInAPackage("..domain.billing..")
                .because("O contexto de Order nao deve depender diretamente do contexto de Billing. " +
                         "Use eventos de dominio (OrderPaid) e a Anti-Corruption Layer (application.acl).");

        rule.check(classes);
    }

    @Test
    void aclIsTheOnlyBridgeBetweenOrderAndBillingContexts() {
        // SOLUÇÃO: fora do ACL, nenhuma classe de application deve depender de domain.billing
        // exceto as que estao explicitamente no pacote acl.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .and().resideOutsideOfPackage("..application.acl..")
                .should().dependOnClassesThat()
                .resideInAPackage("..domain.billing..")
                .because("Apenas a Anti-Corruption Layer (application.acl) deve conhecer " +
                         "tipos do contexto de Billing. Use BillingPaymentTranslator para traducoes.");

        rule.check(classes);
    }

    @Test
    void domainOrderMustNotDependOnInfrastructure() {
        // SOLUÇÃO: o dominio de orders nao deve depender de infraestrutura.
        // Principio: Dependency Rule — dependencias apontam para dentro (em direcao ao dominio).
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.order..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("O dominio nao deve conhecer detalhes de infraestrutura. " +
                         "Use portas de saida (ports/out) para inverter a dependencia.");

        rule.check(classes);
    }
}

