package de.careflow.hl7;

import de.careflow.domain.ClinicalOrderEntity;
import de.careflow.domain.OrderKind;
import de.careflow.domain.OrderStatus;
import de.careflow.domain.PatientEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Hl7GatewayTest {

    private final Hl7Gateway gateway = new Hl7Gateway("CAREFLOW", "LABSYS", "Musterklinikum Nord");

    @Test
    void parsesGeneratedOrm() {
        PatientEntity patient = new PatientEntity();
        patient.setMrn("MKN-10021");
        patient.setGivenName("Elena");
        patient.setFamilyName("Krüger");
        patient.setBirthDate(LocalDate.of(1959, 3, 12));
        patient.setSex("F");
        patient.setWard("Innere 3");
        patient.setBed("12");
        patient.setDepartment("Innere Medizin");

        ClinicalOrderEntity order = new ClinicalOrderEntity();
        order.setPlacerNumber("PLC-TEST");
        order.setCatalogCode("BBCRP");
        order.setDisplayName("Blutbild + CRP");
        order.setKind(OrderKind.LAB);
        order.setStatus(OrderStatus.PLACED);
        order.setOrderedBy("Dr. med. Lena Weber");
        order.setOrderedAt(Instant.parse("2026-08-20T08:00:00Z"));

        Hl7Gateway.ParsedMessage orm = gateway.orm(patient, order);
        assertThat(orm.messageType()).isEqualTo("ORM^O01");
        assertThat(orm.raw()).contains("ORM^O01").contains("MKN-10021").contains("BBCRP");
        assertThat(orm.raw()).contains("ORC|NW").contains("PLC-TEST");
        assertThat(mshApps(orm.raw())).containsExactly("CAREFLOW", "LABSYS");

        Hl7Gateway.ParsedMessage labAck = gateway.ack(orm.controlId(), "O01");
        assertThat(labAck.ackCode()).isEqualTo("AA");
        assertThat(mshApps(labAck.raw())).containsExactly("LABSYS", "CAREFLOW");

        Hl7Gateway.ParsedMessage careflowAck = gateway.ackFromCareflow(orm.controlId(), "O01");
        assertThat(careflowAck.ackCode()).isEqualTo("AA");
        assertThat(mshApps(careflowAck.raw())).containsExactly("CAREFLOW", "LABSYS");

        Hl7Gateway.ParsedMessage oru = gateway.oru(patient, order, List.of());
        assertThat(oru.raw()).contains("ORU^R01");
        assertThat(mshApps(oru.raw())).containsExactly("LABSYS", "CAREFLOW");

        Hl7Gateway.ParsedMessage cancel = gateway.cancelOrm(patient, order);
        assertThat(cancel.messageType()).isEqualTo("ORM^O01");
        assertThat(cancel.raw()).contains("ORC|CA").contains("PLC-TEST");
        assertThat(cancel.raw()).doesNotContain("ORC|NW");
        assertThat(mshApps(cancel.raw())).containsExactly("CAREFLOW", "LABSYS");

        Hl7Gateway.ParsedMessage status = gateway.statusOrm(patient, order);
        assertThat(status.messageType()).isEqualTo("ORM^O01");
        assertThat(status.raw()).contains("ORC|SC").contains("PLC-TEST");
        assertThat(status.raw()).doesNotContain("ORC|NW");
        assertThat(mshApps(status.raw())).containsExactly("LABSYS", "CAREFLOW");
    }

    private static String[] mshApps(String raw) {
        String msh = raw.lines().findFirst().orElse("");
        String[] fields = msh.split("\\|", -1);
        return new String[] {fields[2], fields[4]};
    }
}
