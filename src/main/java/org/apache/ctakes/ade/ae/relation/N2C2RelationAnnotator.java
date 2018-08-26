package org.apache.ctakes.ade.ae.relation;

import org.apache.ctakes.relationextractor.ae.RelationExtractorAnnotator;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
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

    public static final String PARAM_HAS_GOLD_ARGS="HasGoldArgs";
    @ConfigurationParameter(
            name = PARAM_HAS_GOLD_ARGS,
            description = "Whether the annotator has access to gold standard information about arguments to potentially pair up. This may change the way that candidate pairs are formed.",
            mandatory = false
    )
    protected static boolean hasGoldArgs=false;


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
        for(Class<? extends IdentifiedAnnotation> arg1Class : getArg1Class()) {
            for (IdentifiedAnnotation arg1 : JCasUtil.selectCovered(jCas, arg1Class, sentence)) {
                for (IdentifiedAnnotation arg2 : JCasUtil.selectCovered(jCas, getArg2Class(), sentence)) {
                    pairs.add(new IdentifiedAnnotationPair(arg1, arg2));
                }
            }
        }
        return pairs;
    }

    public static AnalysisEngineDescription getGoldArgDataWriterDescription(Class<? extends N2C2RelationAnnotator> relClass, File outputDir, float keepNegativeRate, boolean goldArgs) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(relClass,
                N2C2RelationAnnotator.PARAM_IS_TRAINING,
                true,
                N2C2RelationAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE,
                keepNegativeRate,
                DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
                outputDir,
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
                LibLinearStringOutcomeDataWriter.class,
                N2C2RelationAnnotator.PARAM_HAS_GOLD_ARGS,
                goldArgs);
    }

    public static AnalysisEngineDescription getDataWriterDescription(Class<? extends N2C2RelationAnnotator> relClass, File outputDir, float keepNegativeRate) throws ResourceInitializationException {
        return getGoldArgDataWriterDescription(relClass, outputDir, keepNegativeRate, false);
    }

    public static AnalysisEngineDescription getGoldArgClassifierDescription(Class<? extends N2C2RelationAnnotator> relClass, File modelPath, boolean goldArgs) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(relClass,
                N2C2RelationAnnotator.PARAM_IS_TRAINING,
                false,
                JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
                modelPath,
                N2C2RelationAnnotator.PARAM_HAS_GOLD_ARGS,
                goldArgs);
    }

    public static AnalysisEngineDescription getClassifierDescription(Class<? extends N2C2RelationAnnotator> relClass, File modelPath) throws ResourceInitializationException {
        return getGoldArgClassifierDescription(relClass, modelPath, false);
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

    protected abstract List<Class<? extends IdentifiedAnnotation>> getArg1Class();
    protected abstract Class<? extends IdentifiedAnnotation> getArg2Class();

}
