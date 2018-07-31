package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationFormModifier;

public class MedicationFormAnnotator extends N2C2EntityAnnotator<MedicationFormModifier> {
    @Override
    protected Class<MedicationFormModifier> getEntityClass() {
        return MedicationFormModifier.class;
    }
}
