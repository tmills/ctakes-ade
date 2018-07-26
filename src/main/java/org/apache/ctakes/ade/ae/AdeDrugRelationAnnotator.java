package org.apache.ctakes.ade.ae;

import org.apache.ctakes.ade.type.relation.AdeDrugTextRelation;
import org.apache.ctakes.ade.type.entity.AdverseDrugEventMention;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;

public class AdeDrugRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends IdentifiedAnnotation> getArg1Class() {
        return AdverseDrugEventMention.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationEventMention.class;
    }

    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return AdeDrugTextRelation.class;
    }
}
