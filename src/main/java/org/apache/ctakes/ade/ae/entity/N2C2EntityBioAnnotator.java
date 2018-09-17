package org.apache.ctakes.ade.ae.entity;

import com.google.common.collect.Lists;
import org.apache.ctakes.typesystem.type.constants.CONST;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.*;
import org.cleartk.ml.chunking.BioChunking;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.ml.feature.extractor.CombinedExtractor1;
import org.cleartk.ml.feature.extractor.CoveredTextExtractor;
import org.cleartk.ml.feature.extractor.TypePathExtractor;
import org.cleartk.ml.feature.function.CharacterCategoryPatternFunction;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.JarClassifierFactory;
import org.cleartk.ml.liblinear.LibLinearStringOutcomeDataWriter;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public abstract class N2C2EntityBioAnnotator<T extends IdentifiedAnnotation> extends CleartkAnnotator<String> {

    public static final String PARAM_BACKWARDS = "Backwards";
    @ConfigurationParameter(
            name = PARAM_BACKWARDS,
            description = "Whehter to process the sentence forward or backwards",
            mandatory=false
    )
    private boolean backwards=false;

    private CleartkExtractor<BaseToken, Sentence> contextExtractor;
    private CombinedExtractor1 extractor;
    private BioChunking<BaseToken, T> chunking;

    protected abstract Class<T> getEntityClass();

    protected String getChunkFeatureName(){
        return null;
    }

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.extractor = new CombinedExtractor1(
                new CoveredTextExtractor(),
                CharacterCategoryPatternFunction.createExtractor(CharacterCategoryPatternFunction.PatternType.REPEATS_MERGED),
                new TypePathExtractor(BaseToken.class, "partOfSpeech"));

        this.contextExtractor = new CleartkExtractor(
                BaseToken.class,
                this.extractor,
//                new CleartkExtractor.LastCovered(1),
                new CleartkExtractor.Preceding(3),
                new CleartkExtractor.Following(3));

        if (getChunkFeatureName() != null){
            this.chunking = new BioChunking<>(
                    BaseToken.class,
                    getEntityClass(),
                    getChunkFeatureName()
            );
        }else {
            this.chunking = new BioChunking<>(
                    BaseToken.class,
                    getEntityClass());
        }
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        // get a map from tokens to the ctakes concepts covering them so we can have a feature for whether a token is
        // part of a disease/disorder or sign/symptom, etc.
        Map<BaseToken, Collection<EventMention>> coveringConcepts = JCasUtil.indexCovering(jCas, BaseToken.class, EventMention.class);

        // for each sentence in the document, generate training/classification instances
        for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
            // for each token, extract features and the outcome
            List<BaseToken> tokens = JCasUtil.selectCovered(jCas, BaseToken.class, sentence);
            List<List<Feature>> sentFeatures = new ArrayList<>();
            List<String> outcomes;
            if(this.isTraining()){
                // extract the gold (human annotated) NamedEntityMention annotations
                List<T> entityMentions = JCasUtil.selectCovered(
                        jCas,
                        getEntityClass(),
                        sentence);
                if(getEntityClass().equals(MedicationMention.class)){
                    // don't write training data for entities that were found by the dictionary
                    List<T> toKeep = entityMentions.stream().filter(x -> x.getDiscoveryTechnique() != CONST.NE_DISCOVERY_TECH_DICT_LOOKUP).collect(Collectors.toList());
                    entityMentions = toKeep;
                }

                // convert the NamedEntityMention annotations into token-level BIO outcome labels
                outcomes = this.chunking.createOutcomes(jCas, tokens, entityMentions);
                if(this.backwards){
                    tokens = Lists.reverse(tokens);
                    outcomes = Lists.reverse(outcomes);
                }
            }else{
                if(this.backwards){
                    tokens = Lists.reverse(tokens);
                }
                outcomes = new ArrayList<>();
            }
            int tokenIndex=0;
            for (BaseToken token : tokens) {
                // apply the two feature extractors
                List<Feature> tokenFeatures = new ArrayList<>();
                tokenFeatures.addAll(this.extractor.extract(jCas, token));
                tokenFeatures.addAll(this.contextExtractor.extractWithin(jCas, token, sentence));
                tokenFeatures.add(new Feature("TokenClass", token.getClass().getSimpleName()));

                // features for semantic type of this token:
                Set<Integer> coveringConceptTypes = new HashSet<>();
                for(EventMention event : coveringConcepts.get(token)){
                    if(event.getDiscoveryTechnique() == CONST.NE_DISCOVERY_TECH_DICT_LOOKUP) {
                        coveringConceptTypes.add(event.getTypeID());
                        if (event.getOntologyConceptArr() != null) {
                            for (UmlsConcept umlsConcept : JCasUtil.select(event.getOntologyConceptArr(), UmlsConcept.class)) {
                                tokenFeatures.add(new Feature("CoveringConceptTui", umlsConcept.getTui()));
                            }
                        }
                    }
                }
                for(Integer conceptType : coveringConceptTypes){
                    tokenFeatures.add(new Feature("CoveringConceptType", String.valueOf(conceptType)));
                }
                // Feature for previous class label:
                if(tokenIndex > 0) {
                    tokenFeatures.add(new Feature("PrevOutcome", outcomes.get(tokenIndex - 1)));
                }
                if(this.isTraining()){
                    Instance inst = new Instance(outcomes.get(tokenIndex), tokenFeatures);
                    this.dataWriter.write(inst);
                }else{
                    sentFeatures.add(tokenFeatures);
                    // Rule: A newline token cannot start an entity. This is a hack around the typesystem having
                    // newline token as the same type of thing as a word token and punctuation token.
                    if(token instanceof NewlineToken && tokenIndex > 0 && outcomes.get(tokenIndex-1).equals("O")){
                        outcomes.add("O");
                    }else{
                        outcomes.add(this.classifier.classify(tokenFeatures));
                    }
                }
                tokenIndex++;
            }

            if (!this.isTraining()) {
                if(this.backwards){
                    tokens = Lists.reverse(tokens);
                    outcomes = Lists.reverse(outcomes);
                }
                // create the NamedEntityMention annotations in the CAS
                this.chunking.createChunks(jCas, tokens, outcomes);
            }
        }
    }


    public static AnalysisEngineDescription getDataWriterDescription(Class<? extends N2C2EntityBioAnnotator> annotatorClass, File outputDir) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(annotatorClass,
                CleartkAnnotator.PARAM_IS_TRAINING,
                true,
                DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
                outputDir,
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
                LibLinearStringOutcomeDataWriter.class);
    }

    public static AnalysisEngineDescription getClassifierDescription(Class<? extends N2C2EntityBioAnnotator> annotatorClass, File modelDir) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(annotatorClass,
                CleartkAnnotator.PARAM_IS_TRAINING,
                false,
                JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
                new File(modelDir, "model.jar"));
    }
}
