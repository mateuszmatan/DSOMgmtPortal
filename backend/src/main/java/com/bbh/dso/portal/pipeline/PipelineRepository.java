package com.bbh.dso.portal.pipeline;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PipelineRepository extends JpaRepository<Pipeline, Long> {

    @Query("""
            select p from Pipeline p join fetch p.service s join fetch s.product pr
            where pr.id = :productId order by s.displayOrder, s.name, p.type""")
    List<Pipeline> findByProductId(@Param("productId") Long productId);

    @Query("select p from Pipeline p join fetch p.service s join fetch s.product pr order by pr.name, s.displayOrder, s.name, p.type")
    List<Pipeline> findAllWithService();

    @Query("select p from Pipeline p join fetch p.service s join fetch s.product where p.id = :id")
    Optional<Pipeline> findWithServiceById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pipeline p where p.id = :id")
    Optional<Pipeline> findForUpdate(@Param("id") Long id);

    boolean existsByServiceIdAndType(Long serviceId, PipelineType type);

    @Query("select p.service.product.id, count(p) from Pipeline p group by p.service.product.id")
    List<Object[]> countByProduct();

    @Query("""
            select p.service.product.id, count(distinct p) from Pipeline p join p.keys k
            where k.status = com.bbh.dso.portal.pipeline.KeyStatus.ACTIVE group by p.service.product.id""")
    List<Object[]> countWithActiveKeyByProduct();
}
