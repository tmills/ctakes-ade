package org.apache.ctakes.ade.ae.relation;

import com.google.common.collect.Lists;
import org.apache.ctakes.ade.type.relation.AdeDrugTextRelation;
import org.apache.ctakes.ade.type.entity.AdverseDrugEventMention;
import org.apache.ctakes.relationextractor.ae.RelationExtractorAnnotator;
import org.apache.ctakes.typesystem.type.refsem.SignSymptom;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationEventMention;
import org.apache.ctakes.typesystem.type.textsem.SignSymptomMention;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.*;

public class AdeDrugRelationAnnotator extends JointEntityRelationAnnotator<AdverseDrugEventMention, AdeDrugTextRelation, MedicationEventMention> {

    @Override
    protected Class<AdverseDrugEventMention> getFocusEntityClass() {
        return AdverseDrugEventMention.class;
    }

    @Override
    protected Class<MedicationEventMention> getGivenEntityClass() {
        return MedicationEventMention.class;
    }

    @Override
    protected List<Class<? extends IdentifiedAnnotation>> getProxyClasses() {
        return Lists.newArrayList(SignSymptomMention.class, DiseaseDisorderMention.class);
    }

    @Override
    protected Class<AdeDrugTextRelation> getRelationClass() {
        return AdeDrugTextRelation.class;
    }

    @Override
    protected BinaryTextRelation createProxyRelation(JCas jCas, Annotation arg1, MedicationEventMention arg2) {
        AdeDrugTextRelation rel = new AdeDrugTextRelation(jCas);
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
        List<AdverseDrugEventMention> adverseDrugEventMentions = JCasUtil.selectAt(jCas, AdverseDrugEventMention.class, ent1.getBegin(), ent1.getEnd());
        AdverseDrugEventMention ade;
        if(adverseDrugEventMentions.size() == 0) {
            ade = new AdverseDrugEventMention(jCas, ent1.getBegin(), ent1.getEnd());
            ade.addToIndexes();
        }else{
            ade = adverseDrugEventMentions.get(0);
        }

        RelationArgument arg1 = new RelationArgument(jCas);
        arg1.setArgument(ade);
        arg1.addToIndexes();
        RelationArgument arg2 = new RelationArgument(jCas);
        arg2.setArgument(ent2);
        arg2.addToIndexes();

        AdeDrugTextRelation rel = new AdeDrugTextRelation(jCas);
        rel.setCategory("RelatedTo");
        rel.setArg1(arg1);
        rel.setArg2(arg2);
        rel.addToIndexes();

    }
}
