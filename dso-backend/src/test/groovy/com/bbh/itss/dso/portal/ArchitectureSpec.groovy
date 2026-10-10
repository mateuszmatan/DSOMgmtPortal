package com.bbh.itss.dso.portal

import com.bbh.itss.dso.portal.support.ArchitectureSpecification
import com.tngtech.archunit.lang.ArchRule

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        DsoPortalApplication
    }

    @Override
    List<ArchRule> applicationRules() {
        [noClasses().that().resideOutsideOfPackage('com.bbh.itss.dso.portal.adapter.out.influx..')
                 .should().dependOnClassesThat()
                 .resideInAnyPackage('org.springframework.web.client..', 'java.net.http..')]
    }
}
