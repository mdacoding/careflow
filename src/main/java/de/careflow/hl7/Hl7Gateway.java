package de.careflow.hl7;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import de.careflow.domain.ClinicalOrderEntity;
import de.careflow.domain.ObservationEntity;
import de.careflow.domain.PatientEntity;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class Hl7Gateway {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private final AtomicLong sequence = new AtomicLong(1000);
    private final HapiContext context = new DefaultHapiContext();
    private final String sendingApp;
    private final String receivingApp;
    private final String clinic;

    public Hl7Gateway(
            @Value("${careflow.sending-application:CAREFLOW}") String sendingApp,
            @Value("${careflow.receiving-application:LABSYS}") String receivingApp,
            @Value("${careflow.clinic-name:Musterklinikum Nord}") String clinic) {
        this.sendingApp = sendingApp;
        this.receivingApp = receivingApp;
        this.clinic = clinic.replace(' ', '_').toUpperCase();
        context.getParserConfiguration().setValidating(false);
    }

    @PreDestroy
    public void close() {
        try {
            context.close();
        } catch (Exception ignored) {
            // Parser-Kontext nur für den Prozesslebenszyklus
        }
    }

    public ParsedMessage orm(PatientEntity patient, ClinicalOrderEntity order) {
        return orm(patient, order, "NW");
    }

    public ParsedMessage cancelOrm(PatientEntity patient, ClinicalOrderEntity order) {
        return orm(patient, order, "CA");
    }

    private ParsedMessage orm(PatientEntity patient, ClinicalOrderEntity order, String orderControl) {
        String controlId = nextControl("ORM");
        String ts = TS.format(order.getOrderedAt() == null ? Instant.now() : order.getOrderedAt());
        String raw = String.join("\r",
                "MSH|^~\\&|" + sendingApp + "|" + clinic + "|" + receivingApp + "|" + clinic + "|" + ts
                        + "||ORM^O01|" + controlId + "|P|2.5",
                "PID|1||" + patient.getMrn() + "^^^" + clinic + "||" + patient.getFamilyName() + "^"
                        + patient.getGivenName() + "||" + patient.getBirthDate().toString().replace("-", "")
                        + "|" + patient.getSex(),
                "PV1|1|I|" + patient.getWard() + "^" + patient.getBed() + "|||||||||||||||||||||||||||||||||"
                        + patient.getDepartment(),
                "ORC|" + orderControl + "|" + order.getPlacerNumber() + "|||||||" + ts + "|||" + safe(order.getOrderedBy()),
                "OBR|1|" + order.getPlacerNumber() + "||" + order.getCatalogCode() + "^" + order.getDisplayName() + "^L");
        return parse(raw, "ORM^O01", controlId);
    }

    public ParsedMessage ack(String incomingControlId, String incomingType) {
        String controlId = nextControl("ACK");
        String ts = TS.format(Instant.now());
        String raw = String.join("\r",
                "MSH|^~\\&|" + receivingApp + "|" + clinic + "|" + sendingApp + "|" + clinic + "|" + ts
                        + "||ACK^" + incomingType + "|" + controlId + "|P|2.5",
                "MSA|AA|" + incomingControlId + "|Nachricht angenommen");
        return parse(raw, "ACK", controlId, "AA");
    }

    public ParsedMessage oru(PatientEntity patient, ClinicalOrderEntity order, List<ObservationEntity> observations) {
        String controlId = nextControl("ORU");
        String ts = TS.format(order.getCompletedAt() == null ? Instant.now() : order.getCompletedAt());
        StringBuilder builder = new StringBuilder();
        builder.append(String.join("\r",
                "MSH|^~\\&|" + receivingApp + "|" + clinic + "|" + sendingApp + "|" + clinic + "|" + ts
                        + "||ORU^R01|" + controlId + "|P|2.5",
                "PID|1||" + patient.getMrn() + "^^^" + clinic + "||" + patient.getFamilyName() + "^"
                        + patient.getGivenName() + "||" + patient.getBirthDate().toString().replace("-", "")
                        + "|" + patient.getSex(),
                "ORC|CM|" + order.getPlacerNumber(),
                "OBR|1|" + order.getPlacerNumber() + "||" + order.getCatalogCode() + "^" + order.getDisplayName()
                        + "^L|||" + ts + "|||||||" + ts));
        int seq = 1;
        for (ObservationEntity observation : observations) {
            builder.append("\rOBX|").append(seq++).append("|NM|")
                    .append(observation.getLoinc()).append("^").append(observation.getDisplayName()).append("^LN||")
                    .append(observation.getValueNum() == null ? "" : observation.getValueNum().toPlainString())
                    .append("|").append(safe(observation.getUnit())).append("|")
                    .append(range(observation)).append("|")
                    .append(safe(observation.getInterpretation())).append("|||F");
        }
        return parse(builder.toString(), "ORU^R01", controlId);
    }

    public ParsedMessage parse(String raw, String messageType, String controlId) {
        return parse(raw, messageType, controlId, null);
    }

    public ParsedMessage parse(String raw, String messageType, String controlId, String ackCode) {
        try {
            PipeParser parser = context.getPipeParser();
            Message message = parser.parse(raw.replace('\n', '\r'));
            String encoded = parser.encode(message);
            return new ParsedMessage(messageType, controlId, ackCode, encoded);
        } catch (HL7Exception ex) {
            throw new IllegalStateException("HL7-Nachricht ungültig: " + ex.getMessage(), ex);
        }
    }

    private String nextControl(String prefix) {
        return prefix + sequence.incrementAndGet();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("|", " ");
    }

    private static String range(ObservationEntity observation) {
        if (observation.getRefLow() == null && observation.getRefHigh() == null) {
            return "";
        }
        String low = observation.getRefLow() == null ? "" : observation.getRefLow().toPlainString();
        String high = observation.getRefHigh() == null ? "" : observation.getRefHigh().toPlainString();
        return low + "-" + high;
    }

    public record ParsedMessage(String messageType, String controlId, String ackCode, String raw) {
    }
}
