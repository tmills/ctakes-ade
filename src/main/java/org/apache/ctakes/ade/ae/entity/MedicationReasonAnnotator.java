package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.ade.type.entity.MedicationReasonMention;

public class MedicationReasonAnnotator extends N2C2EntityAnnotator<MedicationReasonMention> {
    @Override
    protected Class<MedicationReasonMention> getEntityClass() {
        return MedicationReasonMention.class;
    }
}
