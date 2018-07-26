package org.apache.ctakes.ade.eval;

import com.google.common.base.Function;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;
import org.apache.ctakes.ade.ae.*;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.*;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.relationextractor.eval.CopyFromGold;
import org.apache.ctakes.relationextractor.eval.RelationExtractorEvaluation;
import org.apache.ctakes.relationextractor.eval.SHARPXMI;
import org.apache.ctakes.relationextractor.eval.XMIReader;
import org.apache.ctakes.typesystem.type.relation.*;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.ctakes.ade.type.entity.*;
import org.apache.ctakes.ade.type.relation.*;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.SerialFormat;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.component.ViewCreatorAnnotator;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.eval.Evaluation_ImplBase;
import org.cleartk.ml.jar.JarClassifierBuilder;
import org.cleartk.util.ViewUriUtil;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EvaluateN2C2Relations extends Evaluation_ImplBase<File,Map<String,AnnotationStatistics<String>>> {

    public static final String GOLD_VIEW_NAME = "GoldView";
    // using same naming scheme as corpus:
    public static final String ADE_DRUG_DIR = "ADE-Drug";
    public static final String DRUG_STRENGTH_DIR = "Strength-Drug";
    public static final String DRUG_ROUTE_DIR = "Route-Drug";
    public static final String DRUG_FREQ_DIR = "Frequency-Drug";
    public static final String DRUG_DOSAGE_DIR = "Dosage-Drug";
    public static final String DRUG_DURATION_DIR = "Duration-Drug";
    public static final String DRUG_REASON_DIR = "Reason-Drug";
    public static final String DRUG_FORM_DIR = "Form-Drug";

    private static final Logger logger = UIMAFramework.getLogger(EvaluateN2C2Relations.class);

    public boolean skipWrite = false;
    public boolean skipTrain = false;
    public double downsample = 1.0;

    public static final String[] REL_TYPES = {DRUG_STRENGTH_DIR, DRUG_ROUTE_DIR,
            DRUG_FREQ_DIR, DRUG_DOSAGE_DIR,
            DRUG_DURATION_DIR, DRUG_REASON_DIR,
            DRUG_FORM_DIR, ADE_DRUG_DIR};

    public Map<String, Class<? extends BinaryTextRelation>> relStringToRelation = null;

    private File outputDir = null;

    /**
     * Create an evaluation that will write all auxiliary files to the given directory.
     *
     * @param baseDirectory The directory for all evaluation files.
     */
    public EvaluateN2C2Relations(File baseDirectory, File outputDir) {
        super(baseDirectory);
        relStringToRelation = new HashMap<>();
        relStringToRelation.put(ADE_DRUG_DIR, AdeDrugTextRelation.class);
        relStringToRelation.put(DRUG_DOSAGE_DIR, MedicationDosageTextRelation.class);
        relStringToRelation.put(DRUG_DURATION_DIR, MedicationDurationTextRelation.class);
        relStringToRelation.put(DRUG_FORM_DIR, MedicationFormTextRelation.class);
        relStringToRelation.put(DRUG_FREQ_DIR, MedicationFrequencyTextRelation.class);
        relStringToRelation.put(DRUG_REASON_DIR, MedicationReasonTextRelation.class);
        relStringToRelation.put(DRUG_ROUTE_DIR, MedicationRouteTextRelation.class);
        relStringToRelation.put(DRUG_STRENGTH_DIR, MedicationStrengthTextRelation.class);
        this.outputDir = outputDir;
        if(!this.outputDir.exists()){
            this.outputDir.mkdirs();
        }
    }

    @Override
    protected CollectionReader getCollectionReader(List<File> items) throws Exception {
        return CollectionReaderFactory.createReader(XMIReader.class,
                XMIReader.PARAM_FILES,
                items);
    }

    @Override
    protected void train(CollectionReader collectionReader, File directory) throws Exception {
        if(skipTrain) return;
        if(!skipWrite) {
            AggregateBuilder builder = new AggregateBuilder();

            builder.add(AnalysisEngineFactory.createEngineDescription(SHARPXMI.DocumentIDAnnotator.class));
            builder.add(AnalysisEngineFactory.createEngineDescription(DocumentIdPrinterAnalysisEngine.class));

            // All the pre-processing is read in through the collection reader, but we need to copy the gold
            // annotations into the default view for the data writers to create features and write instances:
            builder.add(CopyFromGold.getDescription(GOLD_VIEW_NAME,
                    MedicationEventMention.class,
                    AdverseDrugEventMention.class, AdeDrugTextRelation.class,
                    MedicationDosageModifier.class, MedicationDosageTextRelation.class,
                    MedicationDurationModifier.class, MedicationDurationTextRelation.class,
                    MedicationFormModifier.class, MedicationFormTextRelation.class,
                    MedicationFrequencyModifier.class, MedicationFrequencyTextRelation.class,
                    MedicationReasonMention.class, MedicationReasonTextRelation.class,
                    MedicationRouteModifier.class, MedicationRouteTextRelation.class,
                    MedicationStrengthModifier.class, MedicationStrengthTextRelation.class
                    ));
            builder.add(getDataWriters(directory, (float) this.downsample));

            // Put a loop here over relation types and their sub-directories
            SimplePipeline.runPipeline(collectionReader, builder.createAggregate());
        }

        for(String rel : REL_TYPES) {
            JarClassifierBuilder.trainAndPackage(new File(directory, rel), new String[]{"-s", "1", "-c", "1.0"});
        }
    }

    @Override
    protected Map<String,AnnotationStatistics<String>> test(CollectionReader collectionReader, File directory) throws Exception {
        Map<String,AnnotationStatistics<String>> stats = new HashMap<>();
        for(String relType : REL_TYPES){
            stats.put(relType, new AnnotationStatistics<>());
        }
        AggregateBuilder builder = new AggregateBuilder();

        // For this evaluation we assume gold standard entity inputs:
        builder.add(CopyFromGold.getDescription(GOLD_VIEW_NAME,
                MedicationEventMention.class,
                AdverseDrugEventMention.class,
                MedicationDosageModifier.class,
                MedicationDurationModifier.class,
                MedicationFormModifier.class,
                MedicationFrequencyModifier.class,
                MedicationReasonMention.class,
                MedicationRouteModifier.class,
                MedicationStrengthModifier.class
                ));
        builder.add(getRelationAnnotators(directory));
        builder.add(N2C2OutputWriterAnnotator.getDescription(this.outputDir.getAbsolutePath()));

        // Functions needed to evaluate the relations:
        Function<BinaryTextRelation, RelationExtractorEvaluation.HashableArguments> getSpan =
                relation -> new RelationExtractorEvaluation.HashableArguments(relation);
        Function<BinaryTextRelation, String> getOutcome =
                AnnotationStatistics.annotationToFeatureValue("category");

        JCasIterator casIter = new JCasIterator(collectionReader, builder.createAggregate());
        while(casIter.hasNext()){
            JCas jCas = casIter.next();
            JCas goldView = jCas.getView(GOLD_VIEW_NAME);

            for(String relType : REL_TYPES) {
                // get the gold and system annotations
                Collection<? extends BinaryTextRelation> goldBinaryTextRelations =
                        JCasUtil.select(goldView, relStringToRelation.get(relType));
                Collection<? extends BinaryTextRelation> systemBinaryTextRelations =
                        JCasUtil.select(jCas, relStringToRelation.get(relType));
                stats.get(relType).add(goldBinaryTextRelations, systemBinaryTextRelations, getSpan, getOutcome);
            }
        }
        return stats;
    }

    /*
     * To avoid loading and running more expensive cTAKES annotators while tuning hyper-parameters, pre-processing
     * is done by default in an intelligent way -- first we check the xmi directory whether the files read in
     * already have xmi and then create xmi only for those which do not yet exist.
     */
    private static List<File> preprocessXmi(File xmiDir, List<File> fileList) throws UIMAException, IOException {
        File[] xmiFiles = xmiDir.listFiles((dir, name) -> name.endsWith(".xmi"));
        Set<File> processedFiles = Arrays.stream(xmiFiles).collect(Collectors.toSet());
        Set<String> processedFilenames = processedFiles.stream().map(x -> x.getPath()).collect(Collectors.toSet());

        List<File> unprocessedFiles = new ArrayList<>();
        for(File inputFile : fileList){
            File xmiFile = new File(xmiDir, inputFile.getName().replace(".txt", ".xmi"));
            if(!processedFilenames.contains(xmiFile.getPath())){
                unprocessedFiles.add(inputFile);
            }
        }

        logger.log(Level.INFO, "Pre-processing " + unprocessedFiles.size() + " txt files to xmi");
        if(unprocessedFiles.size() > 0) {
            CollectionReader reader = UriCollectionReader.getCollectionReaderFromFiles(unprocessedFiles);

            AggregateBuilder builder = new AggregateBuilder();
            builder.add(UriToDocumentTextAnnotator.getDescription());
            builder.add(getPreprocessingPipeline());
            builder.add(AnalysisEngineFactory.createEngineDescription(ViewCreatorAnnotator.class,
                    ViewCreatorAnnotator.PARAM_VIEW_NAME,
                    GOLD_VIEW_NAME));
            builder.add(AnalysisEngineFactory.createEngineDescription(N2C2Reader.class), CAS.NAME_DEFAULT_SOFA, GOLD_VIEW_NAME);

            for (JCasIterator casIter = new JCasIterator(reader, builder.createAggregate()); casIter.hasNext(); ) {
                JCas jCas = casIter.next();
                File inputFile = new File(ViewUriUtil.getURI(jCas));
                File outputFile = new File(xmiDir, inputFile.getName().replace(".txt", ".xmi"));

                CasIOUtils.save(jCas.getCas(),
                        new FileOutputStream(outputFile),
                        SerialFormat.XMI);
                processedFiles.add(outputFile);
            }
        }
        return new ArrayList<>(processedFiles);
    }

    private static AnalysisEngineDescription getPreprocessingPipeline() throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();
        builder.add( SimpleSegmentAnnotator.createAnnotatorDescription() );
        builder.add( SentenceDetectorAnnotatorBIO.getDescription() );
        builder.add( TokenizerAnnotatorPTB.createAnnotatorDescription() );
        builder.add( AnalysisEngineFactory.createEngineDescription(ParagraphAnnotator.class) );
//        builder.add( LvgAnnotator.createAnnotatorDescription() );
        builder.add( ContextDependentTokenizerAnnotator.createAnnotatorDescription() );
        builder.add( POSTagger.createAnnotatorDescription() );
        builder.add( DefaultJCasTermAnnotator.createAnnotatorDescription() );
        builder.add( ClearNLPDependencyParserAE.createAnnotatorDescription() );
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getDataWriters(File directory, float downsample) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(AdeDrugRelationAnnotator.class, new File(directory, ADE_DRUG_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationStrengthRelationAnnotator.class, new File(directory, DRUG_STRENGTH_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationRouteRelationAnnotator.class, new File(directory, DRUG_ROUTE_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationFrequencyRelationAnnotator.class, new File(directory, DRUG_FREQ_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationDosageRelationAnnotator.class, new File(directory, DRUG_DOSAGE_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationDurationRelationAnnotator.class, new File(directory, DRUG_DURATION_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationReasonRelationAnnotator.class, new File(directory, DRUG_REASON_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationFormRelationAnnotator.class, new File(directory, DRUG_FORM_DIR), downsample));
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getRelationAnnotators(File directory) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();

        builder.add(N2C2RelationAnnotator.getClassifierDescription(AdeDrugRelationAnnotator.class, new File(new File(directory, ADE_DRUG_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationStrengthRelationAnnotator.class, new File(new File(directory, DRUG_STRENGTH_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationReasonRelationAnnotator.class, new File(new File(directory, DRUG_REASON_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationDurationRelationAnnotator.class, new File(new File(directory, DRUG_DURATION_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationDosageRelationAnnotator.class, new File(new File(directory, DRUG_DOSAGE_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationRouteRelationAnnotator.class, new File(new File(directory, DRUG_ROUTE_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationFrequencyRelationAnnotator.class, new File(new File(directory, DRUG_FREQ_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationFormRelationAnnotator.class, new File(new File(directory, DRUG_FORM_DIR), "model.jar")));

        return builder.createAggregateDescription();
    }

    private static List<File> getFilesFromDirectory(File directory){
        return Arrays.asList(directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt");
            }
        }));
    }

    public interface Options {
        @Option(
                shortName = "x",
                longName = "xmi-dir",
                description = "Directory where pre-processed xmi files are stored"
        )
        public File getXmiDir();

        @Option(
                shortName = "t",
                longName = "train-dir",
                description = "Directory with training .txt and .ann files")
        public File getTrainDir();

        @Option(
                defaultToNull=true,
                shortName = "e",
                longName = "eval-dir",
                description = "Directory with .txt and .ann files that will be used for evaluation")
        public File getEvalDir();

        @Option(
                defaultValue="5",
                longName="folds",
                description = "Number of folds to use if doing cross-validation")
        public int getFolds();

        @Option(
                longName="skip-write",
                description="Whether to skip data writing and just perform training and testing (to test new parameters)"
        )
        public boolean getSkipWrite();

        @Option(
                longName="skip-train",
                description="Whether to skip training and just perform testing (e.g., to change output format)"
        )
        public boolean getSkipTrain();

        @Option(
                defaultValue="1.0",
                longName="downsample",
                description="Percentage of negative examples to use"
        )
        public double getDownsample();

        @Option(
                defaultValue="output",
                longName="output-dir",
                description="Directory to write files for n2c2 evaluation"
        )
        public File getOutputDir();
    }
    public static void main(String[] args) throws Exception {
        final Options options = CliFactory.parseArguments(Options.class, args);
        EvaluateN2C2Relations eval = new EvaluateN2C2Relations(new File("target/models/"), options.getOutputDir());
        eval.skipWrite = options.getSkipWrite();
        eval.skipTrain = options.getSkipTrain();
        eval.downsample = options.getDownsample();

        List<File> trainItems = getFilesFromDirectory(options.getTrainDir());
        List<Map<String,AnnotationStatistics<String>>> stats = new ArrayList<>();

        if(options.getEvalDir() != null){
            List<File> evalItems = getFilesFromDirectory(options.getEvalDir());
            List<File> trainXmi = preprocessXmi(options.getXmiDir(), trainItems);
            List<File> evalXmi = preprocessXmi(options.getXmiDir(), evalItems);
            stats.add(eval.trainAndTest(trainXmi, evalXmi));
        }else{
            // run cross-validation
            List<File> trainXmi = preprocessXmi(options.getXmiDir(), trainItems);
            List<Map<String, AnnotationStatistics<String>>> allFoldsStats = eval.crossValidation(trainXmi, options.getFolds());
            stats.addAll(allFoldsStats);
        }

        double macro_f1 = 0;
        double tps = 0, gps = 0, preds =  0;
        for(String relType : REL_TYPES){
            AnnotationStatistics<String> relStats = new AnnotationStatistics<>();
            for(Map<String,AnnotationStatistics<String>> foldStats : stats){
                relStats.addAll(foldStats.get(relType));
            }
            System.out.println(relType + " statistics across folds: ");
            System.out.println(relStats);
            macro_f1 += relStats.f1();
            tps += relStats.countCorrectOutcomes();
            gps += relStats.countReferenceOutcomes();
            preds += relStats.countPredictedOutcomes();
        }

        double micro_r = tps / gps;
        double micro_p = tps / preds;
        double micro_f1 = 2 * micro_r * micro_p / (micro_r + micro_p);

        macro_f1 /= REL_TYPES.length;
        System.out.println("Macro-f1: " + macro_f1);
        System.out.println("Micro-f1: " + micro_f1);
    }
}
