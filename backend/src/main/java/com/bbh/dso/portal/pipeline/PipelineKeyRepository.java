package com.bbh.dso.portal.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PipelineKeyRepository extends JpaRepository<PipelineKey, Long> {

    @Query("""
            select k from PipelineKey k join fetch k.pipeline p join fetch p.service s join fetch s.product
            where k.value = :value""")
    Optional<PipelineKey> findByValue(@Param("value") String value);
}
