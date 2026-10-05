package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.Scanner;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter(autoApply = true)
public class ScannerListConverter implements AttributeConverter<List<Scanner>, String> {

    @Override
    public String convertToDatabaseColumn(List<Scanner> scanners) {
        return scanners == null || scanners.isEmpty() ? null
                : String.join(",", scanners.stream().map(Scanner::name).toList());
    }

    @Override
    public List<Scanner> convertToEntityAttribute(String column) {
        return Text.isBlank(column) ? List.of()
                : Arrays.stream(column.split(",")).map(String::trim).map(Scanner::valueOf).toList();
    }
}
