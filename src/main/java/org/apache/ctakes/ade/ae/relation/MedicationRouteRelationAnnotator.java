package org.apache.ctakes.ade.ae.relation;

import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.ade.type.relation.MedicationRouteTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;
import org.apache.ctakes.typesystem.type.textsem.MedicationRouteModifier;

public class MedicationRouteRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return MedicationRouteTextRelation.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg1Class() {
        return MedicationRouteModifier.class;
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationEventMention.class;
    }
}
