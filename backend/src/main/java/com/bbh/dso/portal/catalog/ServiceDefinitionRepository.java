package com.bbh.dso.portal.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, Long> {

    @Query("""
            select s from ServiceDefinition s join fetch s.product
            where lower(s.metrics.influxProject) = lower(:project) and lower(s.metrics.influxEnv) = lower(:env)""")
    Optional<ServiceDefinition> findByMetricsTags(@Param("project") String project, @Param("env") String env);

    @Query("select s from ServiceDefinition s join fetch s.product where s.sonar.projectKey = :key")
    Optional<ServiceDefinition> findBySonarProjectKey(@Param("key") String key);

    @Query("select s.product.id, count(s) from ServiceDefinition s group by s.product.id")
    List<Object[]> countByProduct();
}
