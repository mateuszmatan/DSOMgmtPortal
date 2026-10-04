package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.Text;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The nested map of one config.yaml entry, written by dotted path such as {@code tools.sonar.projectKey}.
 * Missing values are skipped, so a section only writes what is set.
 */
public class ConfigTree {

    private final Map<String, Object> root = new LinkedHashMap<>();

    /** Sets the value at the path, creating the intermediate maps; null, blank text and empty lists are skipped. */
    public ConfigTree set(String path, Object value) {
        if (value == null || (value instanceof String text && Text.isBlank(text))
                || (value instanceof Collection<?> collection && collection.isEmpty())) {
            return this;
        }
        String[] keys = path.split("\\.");
        Map<String, Object> node = root;
        for (int i = 0; i < keys.length - 1; i++) {
            node = child(node, keys[i]);
        }
        node.put(keys[keys.length - 1], value);
        return this;
    }

    /** Merges a whole map: nested maps merge key by key, any other value replaces what is there. */
    public ConfigTree merge(Map<String, ?> values) {
        merge(root, values);
        return this;
    }

    public Object get(String path) {
        Object node = root;
        for (String key : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(key);
        }
        return node;
    }

    /** The tree with its top-level keys in the given order, other keys after them in insertion order. */
    public Map<String, Object> toMap(List<String> keyOrder) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String key : keyOrder) {
            if (root.containsKey(key)) {
                ordered.put(key, root.get(key));
            }
        }
        root.forEach(ordered::putIfAbsent);
        return ordered;
    }

    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(root);
    }

    /** Every nested map of the tree is created here, so the maps are always mutable and keep insertion order. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> node, String key) {
        if (node.get(key) instanceof Map<?, ?> existing) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        node.put(key, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> base, Map<String, ?> override) {
        for (Map.Entry<String, ?> entry : override.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> overrideMap) {
                merge(child(base, entry.getKey()), (Map<String, ?>) overrideMap);
            } else {
                base.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
