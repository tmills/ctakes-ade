package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationMention;

public class MedicationEntityAnnotator extends N2C2EntityAnnotator<MedicationMention> {
    @Override
    protected Class<MedicationMention> getEntityClass() {
        return MedicationMention.class;
    }
}
