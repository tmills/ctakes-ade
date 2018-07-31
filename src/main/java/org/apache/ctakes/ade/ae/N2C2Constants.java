package org.apache.ctakes.ade.ae;

public class N2C2Constants {
    public static final String DRUG_DIR = "Drug";
    public static final String ADE_DIR = "ADE";
    public static final String DOSAGE_DIR = "Dosage";
    public static final String DURATION_DIR = "Duration";
    public static final String FREQUENCY_DIR = "Frequency";
    public static final String FORM_DIR = "Form";
    public static final String REASON_DIR = "Reason";
    public static final String ROUTE_DIR = "Route";
    public static final String STRENGTH_DIR = "Strength";

    public static final String ADE_DRUG_DIR = "ADE-Drug";
    public static final String DRUG_DOSAGE_DIR = "Dosage-Drug";
    public static final String DRUG_DURATION_DIR = "Duration-Drug";
    public static final String DRUG_FORM_DIR = "Form-Drug";
    public static final String DRUG_FREQ_DIR = "Frequency-Drug";
    public static final String DRUG_REASON_DIR = "Reason-Drug";
    public static final String DRUG_ROUTE_DIR = "Route-Drug";
    public static final String DRUG_STRENGTH_DIR = "Strength-Drug";

    // using same naming scheme as corpus:
    public static final String[] REL_TYPES = {DRUG_STRENGTH_DIR, DRUG_ROUTE_DIR,
            DRUG_FREQ_DIR, DRUG_DOSAGE_DIR,
            DRUG_DURATION_DIR, DRUG_REASON_DIR,
            DRUG_FORM_DIR, ADE_DRUG_DIR};

    public static final String[] ENT_TYPES = {DURATION_DIR, DRUG_DIR,
            ADE_DIR, DOSAGE_DIR,
            DURATION_DIR, FREQUENCY_DIR,
            FORM_DIR, REASON_DIR,
            ROUTE_DIR, STRENGTH_DIR};
}
