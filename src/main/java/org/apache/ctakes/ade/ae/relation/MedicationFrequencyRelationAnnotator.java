package org.apache.ctakes.ade.ae.relation;

import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.ade.type.relation.MedicationFrequencyTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;
import org.apache.ctakes.typesystem.type.textsem.MedicationFrequencyModifier;

public class MedicationFrequencyRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return MedicationFrequencyTextRelation.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg1Class() {
        return MedicationFrequencyModifier.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationEventMention.class;
    }
}
