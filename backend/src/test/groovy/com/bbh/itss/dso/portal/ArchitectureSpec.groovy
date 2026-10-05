package com.bbh.itss.dso.portal

import com.bbh.itss.dso.portal.application.UseCase
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import jakarta.persistence.Converter
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Predicate

import static com.tngtech.archunit.base.DescribedPredicate.not
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

class ArchitectureSpec extends Specification {

    static final String DOMAIN = 'com.bbh.itss.dso.portal.domain..'
    static final String APPLICATION = 'com.bbh.itss.dso.portal.application..'
    static final String ADAPTER_IN = 'com.bbh.itss.dso.portal.adapter.in..'
    static final String ADAPTER_OUT = 'com.bbh.itss.dso.portal.adapter.out..'
    static final String WEB = 'com.bbh.itss.dso.portal.adapter.in.web..'
    static final String PERSISTENCE = 'com.bbh.itss.dso.portal.adapter.out.persistence..'
    static final String INFLUX = 'com.bbh.itss.dso.portal.adapter.out.influx..'
    static final String[] LEGACY = ['com.bbh.itss.dso.portal.catalog..', 'com.bbh.itss.dso.portal.pipeline..',
                                    'com.bbh.itss.dso.portal.dsoconfig..', 'com.bbh.itss.dso.portal.monitoring..',
                                    'com.bbh.itss.dso.portal.evidence..', 'com.bbh.itss.dso.portal.demo..']
    static final String[] FRAMEWORKS = ['jakarta..', 'org.springframework..', 'org.hibernate..', 'tools.jackson..',
                                        'com.fasterxml..', 'org.slf4j..']

    @Shared
    JavaClasses portal = new ClassFileImporter()
            .importLocations([Location.of(DsoPortalApplication.protectionDomain.codeSource.location)])

    def "the import covers the whole main code base"() {
        expect:
        portal.contain(DsoPortalApplication)
        portal.contain(UseCase)
        portal.size() > 100
    }

    def "the domain depends on nothing but Java and itself"() {
        expect:
        holds classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage('java..', DOMAIN)
    }

    def "the application layer depends on nothing but Java, the domain and itself"() {
        expect:
        holds classes().that().resideInAPackage(APPLICATION)
                .should().onlyDependOnClassesThat().resideInAnyPackage('java..', DOMAIN, APPLICATION)
    }

    def "neither the domain nor the application layer knows a framework"() {
        expect:
        holds noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
    }

    def "driving adapters never reach driven adapters"() {
        expect:
        holds noClasses().that().resideInAPackage(ADAPTER_IN)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_OUT)
    }

    def "driven adapters never reach driving adapters"() {
        expect:
        holds noClasses().that().resideInAPackage(ADAPTER_OUT)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_IN)
    }

    def "driving adapters call the application only through its in ports"() {
        expect:
        holds noClasses().that().resideInAPackage(ADAPTER_IN)
                .should().dependOnClassesThat().resideInAPackage('com.bbh.itss.dso.portal.application..port.out..')
    }

    def "controllers and their advice live in the web adapter"() {
        expect:
        holds classes().that().resideOutsideOfPackages(LEGACY)
                .and(describedAs('are controllers or controller advice') { JavaClass type ->
                    [RestController, RestControllerAdvice, Controller].any { type.isAnnotatedWith(it) }
                })
                .should().resideInAPackage(WEB)
    }

    def "entities, embeddables, converters and Spring Data repositories live in the persistence adapter"() {
        expect:
        holds classes().that().resideOutsideOfPackages(LEGACY)
                .and(describedAs('are persistence types') { JavaClass type ->
                    [Entity, Embeddable, MappedSuperclass, Converter].any { type.isAnnotatedWith(it) } ||
                            type.isAssignableTo(Repository)
                })
                .should().resideInAPackage(PERSISTENCE)
    }

    def "only the InfluxDB adapter makes HTTP calls"() {
        expect:
        holds noClasses().that().resideOutsideOfPackage(INFLUX)
                .should().dependOnClassesThat().resideInAnyPackage('org.springframework.web.client..', 'java.net.http..')
    }

    def "the bounded contexts of the domain do not depend on each other in a cycle"() {
        expect:
        holds slices().matching('com.bbh.itss.dso.portal.domain.(*)..').should().beFreeOfCycles()
    }

    def "the application packages form no cycle, so contexts meet only through their ports"() {
        expect:
        holds slices().matching('com.bbh.itss.dso.portal.application.(**)').should().beFreeOfCycles()
    }

    def "the adapter packages form no cycle"() {
        expect:
        holds slices().matching('com.bbh.itss.dso.portal.adapter.(**)').should().beFreeOfCycles()
    }

    def "every use case lives in the application layer and implements one of its in ports"() {
        expect:
        holds classes().that().areAnnotatedWith(UseCase)
                .should().resideInAPackage(APPLICATION)
                .andShould(implementAnInPort())
    }

    def "nothing outside a use case depends on a use case class, only on its in ports"() {
        expect:
        holds noClasses().that(not(annotatedWith(UseCase)))
                .should().dependOnClassesThat().areAnnotatedWith(UseCase)
    }

    def "use cases call other use cases only through their in ports"() {
        expect:
        holds classes().that().areAnnotatedWith(UseCase).should(notDependOnOtherUseCases())
    }

    private boolean holds(ArchRule rule) {
        rule.check(portal)
        true
    }

    private static DescribedPredicate<JavaClass> describedAs(String description, Closure<Boolean> test) {
        DescribedPredicate.describe(description, { JavaClass type -> test(type) } as Predicate<JavaClass>)
    }

    private static ArchCondition<JavaClass> implementAnInPort() {
        new ArchCondition<JavaClass>('implement an interface of a port.in package') {
            @Override
            void check(JavaClass useCase, ConditionEvents events) {
                boolean implementsPort = useCase.allRawInterfaces.any { it.packageName.contains('.port.in') }
                events.add(new SimpleConditionEvent(useCase, implementsPort,
                        "${useCase.name} ${implementsPort ? 'implements' : 'does not implement'} an in port"))
            }
        }
    }

    private static ArchCondition<JavaClass> notDependOnOtherUseCases() {
        new ArchCondition<JavaClass>('not depend on other use case classes') {
            @Override
            void check(JavaClass useCase, ConditionEvents events) {
                useCase.directDependenciesFromSelf
                        .findAll { it.targetClass != useCase && it.targetClass.isAnnotatedWith(UseCase) }
                        .each { events.add(SimpleConditionEvent.violated(it, it.description)) }
            }
        }
    }
}
