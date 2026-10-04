package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.ConfigTree;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.Arrays;
import java.util.List;

/**
 * Which results block the Nexus release and the QC deployment ({@code releaseGate}): the scanners whose
 * limits count, whether the required coverage counts, and the file the gate's state is kept in.
 */
@Embeddable
public record ReleaseGateSettings(
        @NotNull @Size(max = 4)
        @Convert(converter = ScannerListConverter.class)
        @Column(name = "RELEASE_GATE_SCANNERS", length = 100)
        List<@NotNull Scanner> scanners,
        @NotNull
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "RELEASE_GATE_REQUIRE_COVERAGE", nullable = false)
        Boolean requireCoverage,
        @NotBlank @Size(max = 200)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "must be a file name such as release-gate.json")
        @Column(name = "RELEASE_GATE_STATE_FILE", nullable = false, length = 200)
        String stateFile) {

    public ReleaseGateSettings {
        scanners = scanners == null ? List.of() : scanners.stream().distinct().sorted().toList();
        stateFile = Text.trimToNull(stateFile);
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("releaseGate.scanners", scanners.stream().map(Scanner::gateKey).toList())
                .set("releaseGate.requireCoverage", requireCoverage)
                .set("releaseGate.stateFile", stateFile);
    }

    /** Stores the scanners as their names, comma separated, in scanner order. */
    public static class ScannerListConverter implements AttributeConverter<List<Scanner>, String> {

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
}
