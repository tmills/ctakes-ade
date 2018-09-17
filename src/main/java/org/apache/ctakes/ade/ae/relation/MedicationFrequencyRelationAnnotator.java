package org.apache.ctakes.ade.ae.relation;

import com.google.common.collect.Lists;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.ade.type.relation.MedicationFrequencyTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textsem.MedicationFrequencyModifier;

import java.util.List;

public class MedicationFrequencyRelationAnnotator extends N2C2RelationAnnotator {
    @Override
    protected Class<? extends BinaryTextRelation> getRelationClass() {
        return MedicationFrequencyTextRelation.class;
    }

    @Override
    protected List<Class<? extends IdentifiedAnnotation>> getArg1Class() {
        return Lists.newArrayList(MedicationFrequencyModifier.class);
    }

    @Override
    protected Class<? extends IdentifiedAnnotation> getArg2Class() {
        return MedicationMention.class;
    }
}
