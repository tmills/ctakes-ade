package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationFrequencyModifier;

public class MedicationFrequencyAnnotator extends N2C2EntityAnnotator<MedicationFrequencyModifier> {
    @Override
    protected Class<MedicationFrequencyModifier> getEntityClass() {
        return MedicationFrequencyModifier.class;
    }
}
