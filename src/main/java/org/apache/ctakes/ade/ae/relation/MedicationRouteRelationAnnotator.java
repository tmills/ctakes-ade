package org.apache.ctakes.ade.ae.relation;

import com.google.common.collect.Lists;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.ade.type.relation.MedicationRouteTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textsem.MedicationRouteModifier;

import java.util.List;

public class MedicationRouteRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return MedicationRouteTextRelation.class;
    }

    @Override
    protected List<Class<? extends IdentifiedAnnotation>> getArg1Class() {
        return Lists.newArrayList(MedicationRouteModifier.class);
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationMention.class;
    }
}
