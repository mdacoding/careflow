package de.careflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "observations")
public class ObservationEntity {

    @Id
    private String id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String loinc;

    @Column(nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "value_num")
    private BigDecimal valueNum;

    @Column(name = "value_text")
    private String valueText;

    private String unit;
    private String interpretation;

    @Column(name = "ref_low")
    private BigDecimal refLow;

    @Column(name = "ref_high")
    private BigDecimal refHigh;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public boolean abnormal() {
        return interpretation != null && !"N".equals(interpretation);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getLoinc() {
        return loinc;
    }

    public void setLoinc(String loinc) {
        this.loinc = loinc;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getValueNum() {
        return valueNum;
    }

    public void setValueNum(BigDecimal valueNum) {
        this.valueNum = valueNum;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(String interpretation) {
        this.interpretation = interpretation;
    }

    public BigDecimal getRefLow() {
        return refLow;
    }

    public void setRefLow(BigDecimal refLow) {
        this.refLow = refLow;
    }

    public BigDecimal getRefHigh() {
        return refHigh;
    }

    public void setRefHigh(BigDecimal refHigh) {
        this.refHigh = refHigh;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
