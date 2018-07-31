package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;

public class MedicationEntityAnnotator extends N2C2EntityAnnotator<MedicationEventMention> {
    @Override
    protected Class<MedicationEventMention> getEntityClass() {
        return MedicationEventMention.class;
    }
}
