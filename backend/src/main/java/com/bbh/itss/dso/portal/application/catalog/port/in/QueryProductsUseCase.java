package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;

import java.util.List;

public interface QueryProductsUseCase {

    List<ProductSummaryView> list(String search);

    Product get(long id);
}
