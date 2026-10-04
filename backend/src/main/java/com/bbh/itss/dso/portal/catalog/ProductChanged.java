package com.bbh.itss.dso.portal.catalog;

/**
 * Published inside the transaction that added or changed a product and its services, so whatever is derived
 * from them can be brought up to date before the change commits.
 */
public record ProductChanged(Long productId) {
}
