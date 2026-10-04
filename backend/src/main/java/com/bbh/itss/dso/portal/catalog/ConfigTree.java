package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigTree {

    private final Map<String, Object> root = new LinkedHashMap<>();

    public ConfigTree set(String path, Object value) {
        if (isEmpty(value)) {
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

    @SuppressWarnings("unchecked")
    public ConfigTree fillIn(String sectionPath, String key, Object value) {
        if (!isEmpty(value) && get(sectionPath) instanceof Map<?, ?> section && !section.containsKey(key)) {
            ((Map<String, Object>) section).put(key, value);
        }
        return this;
    }

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

    private static boolean isEmpty(Object value) {
        return value == null || (value instanceof String text && Text.isBlank(text))
                || (value instanceof Collection<?> collection && collection.isEmpty())
                || (value instanceof Map<?, ?> map && map.isEmpty());
    }

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
