package org.apache.ctakes.ade.ae;

import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;
import org.apache.ctakes.ade.type.relation.MedicationDosageTextRelation;
import org.apache.ctakes.typesystem.type.textsem.MedicationDosageModifier;

public class MedicationDosageRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return MedicationDosageTextRelation.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg1Class() {
        return MedicationDosageModifier.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationEventMention.class;
    }
}
