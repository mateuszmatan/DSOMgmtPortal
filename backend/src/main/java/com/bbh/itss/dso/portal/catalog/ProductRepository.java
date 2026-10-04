package com.bbh.itss.dso.portal.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByNameAsc();

    Optional<Product> findByCodeIgnoreCase(String code);

    Optional<Product> findByNameIgnoreCase(String name);
}
