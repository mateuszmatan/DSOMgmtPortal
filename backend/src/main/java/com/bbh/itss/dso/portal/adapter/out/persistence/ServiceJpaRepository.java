package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceJpaRepository extends JpaRepository<ServiceEntity, Long> {

    @Query("select s from ServiceEntity s join fetch s.product where s.id = :id")
    Optional<ServiceEntity> findWithProductById(@Param("id") Long id);

    @Query("""
            select s from ServiceEntity s join fetch s.product
            where lower(s.settings.metrics.influxProject) = lower(:project) and lower(s.settings.metrics.influxEnv) = lower(:env)""")
    List<ServiceEntity> findByMetricsTags(@Param("project") String project, @Param("env") String env);

    @Query("select s from ServiceEntity s join fetch s.product where s.settings.sonar.projectKey = :key")
    List<ServiceEntity> findBySonarProjectKey(@Param("key") String key);

    @Query("select s.product.id, count(s) from ServiceEntity s group by s.product.id")
    List<Object[]> countByProduct();
}
