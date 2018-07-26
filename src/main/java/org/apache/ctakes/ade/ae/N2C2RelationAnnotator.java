package org.apache.ctakes.ade.ae;

import org.apache.ctakes.relationextractor.ae.RelationExtractorAnnotator;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.JarClassifierFactory;
import org.cleartk.ml.liblinear.LibLinearStringOutcomeDataWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class N2C2RelationAnnotator extends RelationExtractorAnnotator {

    private static Logger logger = UIMAFramework.getLogger(N2C2RelationAnnotator.class);
    static{
        logger.setLevel(Level.INFO);
    }

    @Override
    protected Class<? extends Annotation> getCoveringClass() {
        return Paragraph.class;
    }

    @Override
    protected Iterable<IdentifiedAnnotationPair> getCandidateRelationArgumentPairs(JCas jCas, Annotation sentence) {
        List<IdentifiedAnnotationPair> pairs = new ArrayList<>();
        for(IdentifiedAnnotation arg1 : JCasUtil.selectCovered(jCas, getArg1Class(), sentence)){
            for(IdentifiedAnnotation arg2 : JCasUtil.selectCovered(jCas, getArg2Class(), sentence)){
                pairs.add(new IdentifiedAnnotationPair(arg1, arg2));
            }
        }
        return pairs;
    }

//    @Override
//    protected String getRelationCategory(Map<List<Annotation>, BinaryTextRelation> relationLookup, IdentifiedAnnotation arg1, IdentifiedAnnotation arg2) {
//        String cat = super.getRelationCategory(relationLookup, arg1, arg2);
//        if(cat == null) cat = RelationExtractorAnnotator.NO_RELATION_CATEGORY;
//        return cat;
//    }

    public static AnalysisEngineDescription getDataWriterDescription(Class<? extends N2C2RelationAnnotator> relClass, File outputDir, float keepNegativeRate) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(relClass,
                AdeDrugRelationAnnotator.PARAM_IS_TRAINING,
                true,
                N2C2RelationAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE,
                keepNegativeRate,
                DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
                outputDir,
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
                LibLinearStringOutcomeDataWriter.class);
    }

    public static AnalysisEngineDescription getClassifierDescription(Class<? extends N2C2RelationAnnotator> relClass, File modelPath) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(relClass,
                AdeDrugRelationAnnotator.PARAM_IS_TRAINING,
                false,
                JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
                modelPath);
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        logger.log(Level.FINE, "Processing in: " + this.getClass().getSimpleName());
        super.process(jCas);
    }

    @Override
    protected abstract Class<? extends BinaryTextRelation> getRelationClass();

    @Override
    protected void createRelation(JCas jCas, IdentifiedAnnotation ent1, IdentifiedAnnotation ent2, String predictedCategory) {
        RelationArgument arg1 = new RelationArgument(jCas);
        arg1.setArgument(ent1);
        arg1.addToIndexes();
        RelationArgument arg2 = new RelationArgument(jCas);
        arg2.setArgument(ent2);
        arg2.addToIndexes();

        Type relType = CasUtil.getType(jCas.getCas(), getRelationClass().getTypeName());
        BinaryTextRelation rel = jCas.getCas().createFS(relType);
        rel.setCategory("RelatedTo");
        rel.setArg1(arg1);
        rel.setArg2(arg2);
        rel.addToIndexes();
    }

    protected abstract Class<? extends IdentifiedAnnotation> getArg1Class();
    protected abstract Class<? extends IdentifiedAnnotation> getArg2Class();

}
