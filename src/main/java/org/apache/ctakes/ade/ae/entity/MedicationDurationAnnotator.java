package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationDurationModifier;

public class MedicationDurationAnnotator extends N2C2EntityAnnotator<MedicationDurationModifier> {

    @Override
    protected Class<MedicationDurationModifier> getEntityClass() {
        return MedicationDurationModifier.class;
    }
}
