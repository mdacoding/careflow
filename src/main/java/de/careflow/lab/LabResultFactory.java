package de.careflow.lab;

import de.careflow.domain.ObservationEntity;
import de.careflow.domain.PatientEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class LabResultFactory {

    public List<ObservationEntity> create(PatientEntity patient, String catalogCode, String orderId) {
        boolean pneumonia = "MKN-10021".equals(patient.getMrn());
        boolean chestPain = "MKN-10022".equals(patient.getMrn());
        boolean heartFailure = "MKN-10024".equals(patient.getMrn());
        List<ObservationEntity> list = new ArrayList<>();
        if ("BB".equals(catalogCode) || "BBCRP".equals(catalogCode)) {
            list.add(obs(orderId, 1, "6690-2", "WBC", "Leukozyten",
                    pneumonia ? "14.8" : "7.2", "10*9/L", "4.0", "10.0"));
            list.add(obs(orderId, 2, "718-7", "HB", "Hämoglobin",
                    pneumonia ? "12.1" : "14.0", "g/dL", "12.0", "16.0"));
            list.add(obs(orderId, 3, "777-3", "PLT", "Thrombozyten",
                    "312", "10*9/L", "150", "400"));
        }
        if ("CRP".equals(catalogCode) || "BBCRP".equals(catalogCode)) {
            list.add(obs(orderId, 4, "1988-5", "CRP", "C-reaktives Protein",
                    pneumonia ? "86" : "3.2", "mg/L", "0", "5"));
        }
        if ("TROP".equals(catalogCode)) {
            list.add(obs(orderId, 1, "10839-9", "TNI", "Troponin I",
                    chestPain ? "0.08" : "0.01", "ng/mL", "0", "0.04"));
        }
        if ("KREA".equals(catalogCode)) {
            list.add(obs(orderId, 1, "2160-0", "CREA", "Kreatinin",
                    heartFailure ? "1.7" : "0.8", "mg/dL", "0.6", "1.1"));
        }
        if ("BGA".equals(catalogCode)) {
            list.add(obs(orderId, 1, "2744-1", "PH", "pH arteriell", "7.41", "", "7.35", "7.45"));
            list.add(obs(orderId, 2, "2703-7", "PO2", "pO2", "78", "mmHg", "75", "100"));
            list.add(obs(orderId, 3, "2019-8", "PCO2", "pCO2", "38", "mmHg", "35", "45"));
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Unbekannter Laborkatalog: " + catalogCode);
        }
        return list;
    }

    private static ObservationEntity obs(
            String orderId,
            int sort,
            String loinc,
            String code,
            String display,
            String value,
            String unit,
            String low,
            String high) {
        ObservationEntity entity = new ObservationEntity();
        entity.setOrderId(orderId);
        entity.setSortOrder(sort);
        entity.setLoinc(loinc);
        entity.setCode(code);
        entity.setDisplayName(display);
        entity.setValueNum(new BigDecimal(value));
        entity.setUnit(unit.isBlank() ? null : unit);
        entity.setRefLow(low.isBlank() ? null : new BigDecimal(low));
        entity.setRefHigh(high.isBlank() ? null : new BigDecimal(high));
        entity.setInterpretation(flag(new BigDecimal(value), entity.getRefLow(), entity.getRefHigh()));
        return entity;
    }

    static String flag(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (low != null && value.compareTo(low) < 0) {
            return "L";
        }
        if (high != null && value.compareTo(high) > 0) {
            return value.compareTo(high.multiply(BigDecimal.TEN)) > 0 ? "HH" : "H";
        }
        return "N";
    }
}
