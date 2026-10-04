package com.bbh.itss.dso.portal.catalog;

/**
 * The descriptive data of a product.
 */
public record ProductDetails(String code, String name, String description, String ownerTeam, String contactEmail) {
}
