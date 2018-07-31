package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationDosageModifier;

public class MedicationDosageAnnotator extends N2C2EntityAnnotator<MedicationDosageModifier> {
    @Override
    protected Class<MedicationDosageModifier> getEntityClass() {
        return MedicationDosageModifier.class;
    }
}
