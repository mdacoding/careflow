package de.careflow.cds;

/**
 * CKD-EPI Kreatinin 2021 ohne Race-Korrektur, Einheit mL/min/1,73 m².
 */
public final class EgfrCalculator {

    private EgfrCalculator() {
    }

    public static Double ckdEpi2021(Double creatinineMgDl, Integer ageYears, String sex) {
        if (creatinineMgDl == null || creatinineMgDl <= 0 || ageYears == null || ageYears <= 0 || sex == null) {
            return null;
        }
        boolean female = "F".equalsIgnoreCase(sex);
        double kappa = female ? 0.7 : 0.9;
        double alpha = female ? -0.241 : -0.302;
        double ratio = creatinineMgDl / kappa;
        double egfr = 142
                * Math.pow(Math.min(ratio, 1.0), alpha)
                * Math.pow(Math.max(ratio, 1.0), -1.200)
                * Math.pow(0.9938, ageYears);
        if (female) {
            egfr *= 1.012;
        }
        return egfr;
    }
}
