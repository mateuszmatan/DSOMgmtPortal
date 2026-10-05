package com.bbh.itss.dso.portal.application.catalog.port.in;

import com.bbh.itss.dso.portal.domain.catalog.Product;

public interface ManageProductsUseCase {

    Product create(ProductCommand command);

    Product update(long id, ProductCommand command);

    void delete(long id);
}
