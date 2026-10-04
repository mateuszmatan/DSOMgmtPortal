package com.bbh.dso.portal.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, Long> {

    Optional<ServiceDefinition> findByInfluxProjectIgnoreCaseAndInfluxEnvIgnoreCase(String influxProject, String influxEnv);

    Optional<ServiceDefinition> findBySonarProjectKey(String sonarProjectKey);

    @Query("select s.product.id, count(s) from ServiceDefinition s group by s.product.id")
    List<Object[]> countByProduct();
}
