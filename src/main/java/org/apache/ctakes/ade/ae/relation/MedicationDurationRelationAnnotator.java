package org.apache.ctakes.ade.ae.relation;

import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.ade.type.relation.MedicationDurationTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationDurationModifier;
import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;

public class MedicationDurationRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends IdentifiedAnnotation> getArg1Class() {
        return MedicationDurationModifier.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationEventMention.class;
    }

    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return MedicationDurationTextRelation.class;
    }
}
