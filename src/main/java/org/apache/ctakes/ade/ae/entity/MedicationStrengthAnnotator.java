package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationStrengthModifier;

public class MedicationStrengthAnnotator extends N2C2EntityAnnotator<MedicationStrengthModifier> {
    @Override
    protected Class<MedicationStrengthModifier> getEntityClass() {
        return MedicationStrengthModifier.class;
    }
}
