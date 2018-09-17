package org.apache.ctakes.ade.ae.relation;

import com.google.common.collect.Lists;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.ade.type.relation.MedicationReasonTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.ade.type.entity.MedicationReasonMention;
import org.apache.ctakes.typesystem.type.textsem.SignSymptomMention;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.List;

public class MedicationReasonRelationAnnotator extends JointEntityRelationAnnotator<MedicationReasonMention, MedicationReasonTextRelation, MedicationMention> {

    @Override
    protected Class<MedicationReasonMention> getFocusEntityClass() {
        return MedicationReasonMention.class;
    }

    @Override
    protected Class<MedicationMention> getGivenEntityClass() {
        return MedicationMention.class;
    }

    @Override
    protected List<Class<? extends IdentifiedAnnotation>> getProxyClasses() {
        return Lists.newArrayList(SignSymptomMention.class, DiseaseDisorderMention.class);
    }

    @Override
    protected Class<MedicationReasonTextRelation> getRelationClass() {
        return MedicationReasonTextRelation.class;
    }

    @Override
    protected BinaryTextRelation createProxyRelation(JCas jCas, Annotation arg1, MedicationMention arg2) {
        MedicationReasonTextRelation rel = new MedicationReasonTextRelation(jCas);
        RelationArgument a1 = new RelationArgument(jCas);
        a1.setArgument(arg1);
        RelationArgument a2 = new RelationArgument(jCas);
        a2.setArgument(arg2);
        rel.setCategory("RelatedTo");
        rel.setArg1(a1);
        rel.setArg2(a2);
        return rel;
    }

    @Override
    protected void createRelation(JCas jCas, IdentifiedAnnotation ent1, IdentifiedAnnotation ent2, String predictedCategory) {
        List<MedicationReasonMention> reasonMentions = JCasUtil.selectAt(jCas, MedicationReasonMention.class, ent1.getBegin(), ent1.getEnd());
        MedicationReasonMention ade;
        if(reasonMentions.size() == 0) {
            ade = new MedicationReasonMention(jCas, ent1.getBegin(), ent1.getEnd());
            ade.addToIndexes();
        }else{
            ade = reasonMentions.get(0);
        }

        RelationArgument arg1 = new RelationArgument(jCas);
        arg1.setArgument(ade);
        arg1.addToIndexes();
        RelationArgument arg2 = new RelationArgument(jCas);
        arg2.setArgument(ent2);
        arg2.addToIndexes();

        MedicationReasonTextRelation rel = new MedicationReasonTextRelation(jCas);
        rel.setCategory("RelatedTo");
        rel.setArg1(arg1);
        rel.setArg2(arg2);
        rel.addToIndexes();

    }

}
