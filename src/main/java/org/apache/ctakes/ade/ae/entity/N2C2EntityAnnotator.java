package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.ade.ae.relation.N2C2RelationAnnotator;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.CleartkSequenceAnnotator;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
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
import java.util.ArrayList;
import java.util.List;

public abstract class N2C2EntityAnnotator<T extends IdentifiedAnnotation> extends CleartkAnnotator<String> {

    private CleartkExtractor<BaseToken, Sentence> contextExtractor;
    private CombinedExtractor1 extractor;
    private BioChunking<BaseToken, T> chunking;

    protected abstract Class<T> getEntityClass();

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
                new CleartkExtractor.Preceding(3),
                new CleartkExtractor.Following(3));

        this.chunking = new BioChunking<>(
                BaseToken.class,
                getEntityClass());
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        // for each sentence in the document, generate training/classification instances
        for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
            List<List<Feature>> tokenFeatureLists = new ArrayList<>();

            // for each token, extract features and the outcome
            List<BaseToken> tokens = JCasUtil.selectCovered(jCas, BaseToken.class, sentence);
            for (BaseToken token : tokens) {
                // apply the two feature extractors
                List<Feature> tokenFeatures = new ArrayList<>();
                tokenFeatures.add(new Feature("TokenClass", token.getClass().getSimpleName()));
                tokenFeatures.addAll(this.extractor.extract(jCas, token));
                tokenFeatures.addAll(this.contextExtractor.extractWithin(jCas, token, sentence));
                tokenFeatureLists.add(tokenFeatures);
            }

            if (this.isTraining()) {

                // extract the gold (human annotated) NamedEntityMention annotations
                List<T> entityMentions = JCasUtil.selectCovered(
                        jCas,
                        getEntityClass(),
                        sentence);

                // convert the NamedEntityMention annotations into token-level BIO outcome labels
                List<String> outcomes = this.chunking.createOutcomes(jCas, tokens, entityMentions);

                // write the features and outcomes as training instances
                for(int i = 0; i < outcomes.size(); i++){
                    this.dataWriter.write(new Instance(outcomes.get(i), tokenFeatureLists.get(i)));
                }
            }

            // for classification, set the token part of speech tags from the classifier outcomes.
            else {
                // get the predicted BIO outcome labels from the classifier
                List<String> outcomes = new ArrayList<>();
                for(int i = 0; i < tokenFeatureLists.size(); i++){
                    // Rule: A newline token cannot start an entity. This is a hack around the typesystem having
                    // newline token as the same type of thing as a word token and punctuation token.
                    if(tokens.get(i) instanceof NewlineToken && i > 0 && outcomes.get(i - 1).equals("O")) {
                        outcomes.add("O");
                    }else {
                        outcomes.add(this.classifier.classify(tokenFeatureLists.get(i)));
                    }
                }

                // create the NamedEntityMention annotations in the CAS
                this.chunking.createChunks(jCas, tokens, outcomes);
            }
        }
    }


    public static AnalysisEngineDescription getDataWriterDescription(Class<? extends N2C2EntityAnnotator> annotatorClass, File outputDir) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(annotatorClass,
                CleartkSequenceAnnotator.PARAM_IS_TRAINING,
                true,
                DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
                outputDir,
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
                LibLinearStringOutcomeDataWriter.class);
    }

    public static AnalysisEngineDescription getClassifierDescription(Class<? extends N2C2EntityAnnotator> annotatorClass, File modelDir) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(annotatorClass,
                CleartkSequenceAnnotator.PARAM_IS_TRAINING,
                false,
                JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
                new File(modelDir, "model.jar"));
    }
}
