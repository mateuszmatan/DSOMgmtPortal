package com.bbh.itss.dso.portal.settings;

/**
 * Published inside the transaction that changed the global settings, which every pipeline's configuration
 * depends on.
 */
public record GlobalSettingsChanged() {
}
