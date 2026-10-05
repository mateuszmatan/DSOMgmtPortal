package com.bbh.itss.dso.portal.adapter.out.influx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FluxCsv {

    private FluxCsv() {
    }

    static List<Map<String, String>> parse(String csv) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        List<String> header = null;
        for (String line : csv.split("\r?\n")) {
            if (line.isBlank()) {
                header = null;
                continue;
            }
            List<String> cells = splitLine(line);
            if (header == null || cells.equals(header)) {
                header = cells;
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            int first = header.size() > 2 && header.get(1).equals("result") && header.get(2).equals("table") ? 2 : 0;
            for (int i = first; i < header.size() && i < cells.size(); i++) {
                if (!header.get(i).isEmpty()) {
                    row.put(header.get(i), cells.get(i));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    static List<String> splitLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
