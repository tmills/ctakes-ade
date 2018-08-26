package org.apache.ctakes.ade.ae.relation;

import com.google.common.collect.Lists;
import org.apache.ctakes.dependency.parser.util.DependencyUtility;
import org.apache.ctakes.relationextractor.ae.RelationExtractorAnnotator;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.syntax.ConllDependencyNode;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.JarClassifierFactory;
import org.cleartk.ml.liblinear.LibLinearStringOutcomeDataWriter;
import org.cleartk.util.ViewUriUtil;

import java.io.File;
import java.util.*;

public abstract class JointEntityRelationAnnotator
        <FOCUS_ENTITY_TYPE extends IdentifiedAnnotation,
                FOCUS_RELATION_TYPE extends BinaryTextRelation,
                GIVEN_ENTITY_TYPE extends IdentifiedAnnotation>
        extends RelationExtractorAnnotator {

    private static Logger logger = UIMAFramework.getLogger(JointEntityRelationAnnotator.class);
    public static final String PARAM_HAS_GOLD_ARGS="HasGoldArgs";
    @ConfigurationParameter(
            name = PARAM_HAS_GOLD_ARGS,
            description = "Whether the annotator has access to gold standard information about arguments to potentially pair up. This may change the way that candidate pairs are formed.",
            mandatory = false
    )
    protected boolean hasGoldArgs=false;

    @Override
    protected Map<List<Annotation>, BinaryTextRelation> getRelationLookup(JCas jCas) throws AnalysisEngineProcessException {
        Map<List<Annotation>, BinaryTextRelation> relationLookup = getGoldRelations(jCas);
        if(!this.hasGoldArgs) {
            mapRelationArguments(jCas, relationLookup);
        }
        return relationLookup;
    }

    protected Map<List<Annotation>, BinaryTextRelation> getGoldRelations(JCas jCas) throws AnalysisEngineProcessException {
        Map<List<Annotation>, BinaryTextRelation> relationLookup = new HashMap<>();
        for (FOCUS_RELATION_TYPE relation : JCasUtil.select(jCas, this.getRelationClass())) {
            Annotation arg1 = relation.getArg1().getArgument();
            Annotation arg2 = relation.getArg2().getArgument();
            // The key is a list of args so we can do bi-directional lookup
            List<Annotation> key = Arrays.asList(arg1, arg2);
            if(relationLookup.containsKey(key)){
                String reln = relationLookup.get(key).getCategory();
                System.err.println("Error in: "+ ViewUriUtil.getURI(jCas).toString());
                System.err.println("Error! This attempted relation " + relation.getCategory() + " already has a relation " + reln + " at this span: " + arg1.getCoveredText() + " -- " + arg2.getCoveredText());
            }
            relationLookup.put(key, relation);
        }
        return relationLookup;
    }

    // Our relations are defined as FOCUS_RELATION_TYPE( FOCUS_ENTITY_TYPE, GIVEN_ENTITY_TYPE ) in the gold standard
    // but FOCUS_ENTITY_TYPE is not well defined otherwise. So we used the classes returned by getProxyClasses() as
    // substitutes by mapping them here.
    protected void mapRelationArguments(JCas jCas, Map<List<Annotation>, BinaryTextRelation> relationLookup){
        Map<ConllDependencyNode, Annotation> proxyHeads = new HashMap<>();
        Map<FOCUS_ENTITY_TYPE, ConllDependencyNode> focusHeads = new HashMap<>();

        // populate mapping from focus types to dependency heads:
        JCasUtil.select(jCas, getFocusEntityClass()).forEach(focus_entity_type -> focusHeads.put(focus_entity_type, DependencyUtility.getNominalHeadNode(jCas, focus_entity_type)));

        // populate mapping from dependency heads to subsitute types:
        for(Class<? extends Annotation> subClass : getProxyClasses()){
            JCasUtil.select(jCas, subClass).forEach(ent -> proxyHeads.put(DependencyUtility.getNominalHeadNode(jCas, ent), ent));
        }

        // now, for every relation, take its focus class argument (provided by gold standard at training time) and map
        // it to a proxy class argument through the dependency headwords maps
        List<BinaryTextRelation> toRemove = new ArrayList<>();
        List<BinaryTextRelation> toAdd = new ArrayList<>();
        for(FOCUS_RELATION_TYPE rel : JCasUtil.select(jCas, getRelationClass())){
            FOCUS_ENTITY_TYPE arg1 = (FOCUS_ENTITY_TYPE) rel.getArg1().getArgument();
            GIVEN_ENTITY_TYPE arg2 = (GIVEN_ENTITY_TYPE) rel.getArg2().getArgument();
            ConllDependencyNode head = focusHeads.get(arg1);
            if(head != null){
                Annotation proxyEnt = proxyHeads.get(head);
                BinaryTextRelation newRel = createProxyRelation(jCas, proxyEnt, (GIVEN_ENTITY_TYPE) rel.getArg2().getArgument());
                relationLookup.put(Lists.newArrayList(newRel.getArg1().getArgument(), arg2), newRel);
                toAdd.add(newRel);
            }
            toRemove.add(rel);
        }
        logger.log(Level.INFO, String.format("Mapping complete for this document: %d gold relations mapped to %d with system arguments.", toRemove.size(), toAdd.size()));
        toRemove.forEach(x -> x.removeFromIndexes());
        toAdd.forEach(x -> x.addToIndexes());
    }

    protected abstract Class<FOCUS_ENTITY_TYPE> getFocusEntityClass();
    protected abstract Class<GIVEN_ENTITY_TYPE> getGivenEntityClass();
    protected abstract List<Class<? extends IdentifiedAnnotation>> getProxyClasses();
    protected abstract Class<FOCUS_RELATION_TYPE> getRelationClass();
    protected abstract BinaryTextRelation createProxyRelation(JCas jCas, Annotation arg1, GIVEN_ENTITY_TYPE arg2);

    @Override
    protected Iterable<IdentifiedAnnotationPair> getCandidateRelationArgumentPairs(JCas jCas, Annotation coveringAnnotation) {
        List<IdentifiedAnnotationPair> pairs = new ArrayList<>();
        List<IdentifiedAnnotation> arg1s = new ArrayList<>();
        if(this.hasGoldArgs){
            arg1s.addAll(JCasUtil.selectCovered(jCas, getFocusEntityClass(), coveringAnnotation));
        }else{
            getProxyClasses().forEach(proxyClass -> arg1s.addAll(JCasUtil.selectCovered(jCas, proxyClass, coveringAnnotation)));
        }
        for (IdentifiedAnnotation arg1 : arg1s) {
            for (IdentifiedAnnotation arg2 : JCasUtil.selectCovered(jCas, getGivenEntityClass(), coveringAnnotation)) {
                pairs.add(new IdentifiedAnnotationPair(arg1, arg2));
            }
        }
        return pairs;
    }

    @Override
    protected Class<? extends Annotation> getCoveringClass() {
        return Paragraph.class;
    }

    public static AnalysisEngineDescription getDataWriterDescription(Class<? extends JointEntityRelationAnnotator> relClass, File outputDir, float keepNegativeRate, boolean goldArgs) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(relClass,
                CleartkAnnotator.PARAM_IS_TRAINING,
                true,
                RelationExtractorAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE,
                keepNegativeRate,
                DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
                outputDir,
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
                LibLinearStringOutcomeDataWriter.class,
                JointEntityRelationAnnotator.PARAM_HAS_GOLD_ARGS,
                goldArgs);
    }

    public static AnalysisEngineDescription getClassifierDescription(Class<? extends JointEntityRelationAnnotator> relClass, File modelPath, boolean goldArgs) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(relClass,
                CleartkAnnotator.PARAM_IS_TRAINING,
                false,
                JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
                modelPath,
                JointEntityRelationAnnotator.PARAM_HAS_GOLD_ARGS,
                goldArgs);
    }
}
