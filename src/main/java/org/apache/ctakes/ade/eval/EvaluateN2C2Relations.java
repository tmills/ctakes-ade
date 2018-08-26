package org.apache.ctakes.ade.eval;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;
import org.apache.ctakes.ade.ae.N2C2Constants;
import org.apache.ctakes.ade.ae.N2C2OutputWriterAnnotator;
import org.apache.ctakes.ade.ae.N2C2Reader;
import org.apache.ctakes.ade.ae.entity.*;
import org.apache.ctakes.ade.ae.relation.*;
import org.apache.ctakes.ade.type.entity.AdverseDrugEventMention;
import org.apache.ctakes.ade.type.entity.MedicationReasonMention;
import org.apache.ctakes.ade.type.relation.*;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.*;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.relationextractor.eval.CopyFromGold;
import org.apache.ctakes.relationextractor.eval.RelationExtractorEvaluation;
import org.apache.ctakes.relationextractor.eval.SHARPXMI;
import org.apache.ctakes.relationextractor.eval.XMIReader;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.textsem.*;
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
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
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
    enum RUN_MODE {JOINT, ENT, RELS};

    private static final Logger logger = UIMAFramework.getLogger(EvaluateN2C2Relations.class);

    public boolean skipWrite = false;
    public boolean skipTrain = false;
    public double downsample = 1.0;

    public Map<String, Class<? extends IdentifiedAnnotation>> entStringToRelation = null;
    public Map<String, Class<? extends BinaryTextRelation>> relStringToRelation = null;

    private File outputDir = null;
    private RUN_MODE runMode;

    /**
     * Create an evaluation that will write all auxiliary files to the given directory.
     *
     * @param baseDirectory The directory for all evaluation files.
     */
    public EvaluateN2C2Relations(File baseDirectory, File outputDir, RUN_MODE runMode) {
        super(baseDirectory);
        relStringToRelation = new HashMap<>();
        relStringToRelation.put(N2C2Constants.ADE_DRUG_DIR, AdeDrugTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_DOSAGE_DIR, MedicationDosageTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_DURATION_DIR, MedicationDurationTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_FORM_DIR, MedicationFormTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_FREQ_DIR, MedicationFrequencyTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_REASON_DIR, MedicationReasonTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_ROUTE_DIR, MedicationRouteTextRelation.class);
        relStringToRelation.put(N2C2Constants.DRUG_STRENGTH_DIR, MedicationStrengthTextRelation.class);
        entStringToRelation = new HashMap<>();
        entStringToRelation.put(N2C2Constants.ADE_DIR, AdverseDrugEventMention.class);
        entStringToRelation.put(N2C2Constants.DOSAGE_DIR, MedicationDosageModifier.class);
        entStringToRelation.put(N2C2Constants.DRUG_DIR, MedicationEventMention.class);
        entStringToRelation.put(N2C2Constants.DURATION_DIR, MedicationDurationModifier.class);
        entStringToRelation.put(N2C2Constants.FORM_DIR, MedicationFormModifier.class);
        entStringToRelation.put(N2C2Constants.FREQUENCY_DIR, MedicationFrequencyModifier.class);
        entStringToRelation.put(N2C2Constants.REASON_DIR, MedicationReasonMention.class);
        entStringToRelation.put(N2C2Constants.ROUTE_DIR, MedicationRouteModifier.class);
        entStringToRelation.put(N2C2Constants.STRENGTH_DIR, MedicationStrengthModifier.class);

        this.outputDir = outputDir;
        this.runMode = runMode;
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
                    AdverseDrugEventMention.class,
                    AdeDrugTextRelation.class,
                    MedicationDosageModifier.class, MedicationDosageTextRelation.class,
                    MedicationDurationModifier.class, MedicationDurationTextRelation.class,
                    MedicationFormModifier.class, MedicationFormTextRelation.class,
                    MedicationFrequencyModifier.class, MedicationFrequencyTextRelation.class,
                    MedicationReasonMention.class,
                    MedicationReasonTextRelation.class,
                    MedicationRouteModifier.class, MedicationRouteTextRelation.class,
                    MedicationStrengthModifier.class, MedicationStrengthTextRelation.class
                    ));
            // if we are doing only rels or only ents, then don't bother doing model-building for the other:
            if(this.runMode == RUN_MODE.ENT || this.runMode == RUN_MODE.JOINT) {
                builder.add(getEntityDataWriters(directory));
            }
            if(this.runMode == RUN_MODE.RELS || this.runMode == RUN_MODE.JOINT) {
                builder.add(getRelationDataWriters(directory, (float) this.downsample));
            }
            boolean goldArgs = (this.runMode == RUN_MODE.RELS);
            builder.add(JointEntityRelationAnnotator.getDataWriterDescription(AdeDrugRelationAnnotator.class,
                    new File(directory, N2C2Constants.ADE_DRUG_DIR),
                    (float) this.downsample,
                    goldArgs));
            builder.add(JointEntityRelationAnnotator.getDataWriterDescription(MedicationReasonRelationAnnotator.class,
                    new File(directory, N2C2Constants.DRUG_REASON_DIR),
                    (float) this.downsample,
                    goldArgs));

            // Put a loop here over relation types and their sub-directories
            SimplePipeline.runPipeline(collectionReader, builder.createAggregate());
        }

        // train entity classifiers:
        if(this.runMode != RUN_MODE.RELS) {
            for (String ent : N2C2Constants.ENT_TYPES) {
                // There are 2 entity types that we don't actually model as entity sequence tagging:
                if(ent.equals(N2C2Constants.ADE_DIR) || ent.equals(N2C2Constants.REASON_DIR)) continue;

                JarClassifierBuilder.trainAndPackage(new File(directory, ent), new String[]{"-s", "1", "-c", "0.1"});
            }
        }

        // train relation classifiers:
        if(this.runMode != RUN_MODE.ENT) {
            for (String rel : N2C2Constants.REL_TYPES) {
                JarClassifierBuilder.trainAndPackage(new File(directory, rel), new String[]{"-s", "1", "-c", "0.1"});
            }
        }

        // Train joint entity/relation classifiers:
        JarClassifierBuilder.trainAndPackage(new File(directory, N2C2Constants.ADE_DRUG_DIR), new String[]{"-s", "1", "-c", "0.1"});
        JarClassifierBuilder.trainAndPackage(new File(directory, N2C2Constants.DRUG_REASON_DIR), new String[]{"-s", "1", "-c", "0.1"});
    }

    @Override
    protected Map<String,AnnotationStatistics<String>> test(CollectionReader collectionReader, File directory) throws Exception {
        Map<String,AnnotationStatistics<String>> stats = new HashMap<>();
        for(String entType : N2C2Constants.ENT_TYPES){
            stats.put(entType, new AnnotationStatistics<>());
        }
        for(String relType : N2C2Constants.REL_TYPES){
            stats.put(relType, new AnnotationStatistics<>());
        }
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(AnalysisEngineFactory.createEngineDescription(SHARPXMI.CopyDocumentTextToGoldView.class));
        builder.add(AnalysisEngineFactory.createEngineDescription(SHARPXMI.DocumentIDAnnotator.class));
        builder.add(AnalysisEngineFactory.createEngineDescription(DocumentIdPrinterAnalysisEngine.class));

        if(this.runMode == RUN_MODE.RELS) {
            // For rel-only evaluation we assume gold standard entity inputs:
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
        }else{
            // for ent-only or joint evaluation we need to add entity annotators:
            builder.add(getEntityAnnotators(directory));
        }
        if(this.runMode != RUN_MODE.ENT) {
            builder.add(getRelationAnnotators(directory));
        }
        boolean goldArgs = (this.runMode == RUN_MODE.RELS);
        builder.add(JointEntityRelationAnnotator.getClassifierDescription(
                AdeDrugRelationAnnotator.class,
                new File(new File(directory, N2C2Constants.ADE_DRUG_DIR), "model.jar"), goldArgs));
        builder.add(JointEntityRelationAnnotator.getClassifierDescription(
                MedicationReasonRelationAnnotator.class,
                new File(new File(directory, N2C2Constants.DRUG_REASON_DIR), "model.jar"), goldArgs));

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

            for(String entType : N2C2Constants.ENT_TYPES) {
                Set<? extends IdentifiedAnnotation> goldEntities =
                        new HashSet(JCasUtil.select(goldView, entStringToRelation.get(entType)));
                Set<? extends IdentifiedAnnotation> systemEntities =
                        new HashSet(JCasUtil.select(jCas, entStringToRelation.get(entType)));
                Set<ComparableEntity> goldHash = new HashSet();
                Set<ComparableEntity> sysHash = new HashSet();
                stats.get(entType).add(goldEntities, systemEntities);

                // try to do error analysis by creating hashable sets (so equivalanet uima spans are the same)
                goldEntities.stream().forEach(x -> goldHash.add(new ComparableEntity(x)));
                systemEntities.stream().forEach(x -> sysHash.add(new ComparableEntity(x)));

                Set<ComparableEntity> fns = Sets.difference(goldHash, sysHash);
                Set<ComparableEntity> fps = Sets.difference(sysHash, goldHash);

                for(ComparableEntity fp : fps){
                    System.out.println("FP: " + fp.toString());
                }
                for(ComparableEntity fn : fns){
                    System.out.println("FN: " + fn.toString());
                }
            }
            for(String relType : N2C2Constants.REL_TYPES) {
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
        Set<File> existingFiles = Arrays.stream(xmiFiles).collect(Collectors.toSet());
        Set<String> existingFilenames = existingFiles.stream().map(x -> x.getPath()).collect(Collectors.toSet());
        List<File> processedFiles = new ArrayList<>();
        List<File> unprocessedFiles = new ArrayList<>();

        for(File inputFile : fileList){
            File xmiFile = new File(xmiDir, inputFile.getName().replace(".txt", ".xmi"));
            if(existingFilenames.contains(xmiFile.getPath())) {
                processedFiles.add(xmiFile);
            } else {
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
        builder.add( ContextDependentTokenizerAnnotator.createAnnotatorDescription() );
        builder.add( POSTagger.createAnnotatorDescription() );
        builder.add( DefaultJCasTermAnnotator.createAnnotatorDescription() );
        builder.add( ClearNLPDependencyParserAE.createAnnotatorDescription() );
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getEntityDataWriters(File directory) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();

        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationDosageAnnotator.class, new File(directory, N2C2Constants.DOSAGE_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationDurationAnnotator.class, new File(directory, N2C2Constants.DURATION_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationEntityAnnotator.class, new File(directory, N2C2Constants.DRUG_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationFormAnnotator.class, new File(directory, N2C2Constants.FORM_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationFrequencyAnnotator.class, new File(directory, N2C2Constants.FREQUENCY_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationRouteAnnotator.class, new File(directory, N2C2Constants.ROUTE_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MedicationStrengthAnnotator.class, new File(directory, N2C2Constants.STRENGTH_DIR)));
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getRelationDataWriters(File directory, float downsample) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationStrengthRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_STRENGTH_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationRouteRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_ROUTE_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationFrequencyRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_FREQ_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationDosageRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_DOSAGE_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationDurationRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_DURATION_DIR), downsample));
        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationFormRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_FORM_DIR), downsample));
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getEntityAnnotators(File directory) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationDosageAnnotator.class, new File(directory, N2C2Constants.DOSAGE_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationDurationAnnotator.class, new File(directory, N2C2Constants.DURATION_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationEntityAnnotator.class, new File(directory, N2C2Constants.DRUG_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationFrequencyAnnotator.class, new File(directory, N2C2Constants.FREQUENCY_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationFormAnnotator.class, new File(directory, N2C2Constants.FORM_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationRouteAnnotator.class, new File(directory, N2C2Constants.ROUTE_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MedicationStrengthAnnotator.class, new File(directory, N2C2Constants.STRENGTH_DIR)));

        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getRelationAnnotators(File directory) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();

        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationStrengthRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_STRENGTH_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationDurationRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_DURATION_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationDosageRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_DOSAGE_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationRouteRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_ROUTE_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationFrequencyRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_FREQ_DIR), "model.jar")));
        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationFormRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_FORM_DIR), "model.jar")));

        return builder.createAggregateDescription();
    }

    public static class ComparableEntity {
        private String entType, text;
        private int begin, end;

        public ComparableEntity(IdentifiedAnnotation a){
            entType = a.getClass().getSimpleName();
            begin = a.getBegin();
            end = a.getEnd();
            text = a.getCoveredText().replace("\n", " ");
        }

        @Override
        public int hashCode() {
            return this.entType.hashCode() + Integer.hashCode(begin) + Integer.hashCode(end);
        }

        @Override
        public boolean equals(Object obj) {
            ComparableEntity other = (ComparableEntity) obj;
            return this.entType.equals(other.entType) &&
                    this.begin == other.begin &&
                    this.end == other.end;
        }

        @Override
        public String toString() {
            return String.format("%s [%s] (%d, %d)", this.text, this.entType, this.begin, this.end);
        }
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

        @Option(
                defaultValue="R",
                longName="run-mode",
                description="Evaluate for entities (E), rels (R), or both jointly (J)"
        )
        public String getRunMode();
    }

    public static void main(String[] args) throws Exception {
        final Options options = CliFactory.parseArguments(Options.class, args);
        RUN_MODE runMode=RUN_MODE.JOINT;
        switch(options.getRunMode()){
            case "E":
                runMode = RUN_MODE.ENT;
                break;
            case "R":
                runMode = RUN_MODE.RELS;
                break;
            case "J":
                runMode = RUN_MODE.JOINT;
                break;
            default:
                System.err.println("Unreocgnized option " + options.getRunMode());
        }
        EvaluateN2C2Relations eval = new EvaluateN2C2Relations(new File("target/models/"), options.getOutputDir(), runMode);
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


        if(runMode != RUN_MODE.RELS){
            System.out.println("Entity scoring: ");
            double macro_f1 = 0;
            double tps = 0, gps = 0, preds = 0;
            for(String entType : N2C2Constants.ENT_TYPES) {
                AnnotationStatistics<String> entStats = new AnnotationStatistics<>();
                for(Map<String,AnnotationStatistics<String>> foldStats : stats){
                    entStats.addAll(foldStats.get(entType));
                }
                System.out.println(entType + " statistics across folds: ");
                System.out.println(entStats);
                macro_f1 += entStats.f1();
                tps += entStats.countCorrectOutcomes();
                gps += entStats.countReferenceOutcomes();
                preds += entStats.countPredictedOutcomes();
            }
            double micro_r = tps / gps;
            double micro_p = tps / preds;
            double micro_f1 = 2 * micro_r * micro_p / (micro_r + micro_p);

            macro_f1 /= N2C2Constants.ENT_TYPES.length;
            System.out.println("Macro-f1: " + macro_f1);
            System.out.println("Micro-f1: " + micro_f1);
        }
        if(runMode != RUN_MODE.ENT) {
            System.out.println("Relation scoring:");
            double macro_f1 = 0;
            double tps = 0, gps = 0, preds = 0;
            for (String relType : N2C2Constants.REL_TYPES) {
                AnnotationStatistics<String> relStats = new AnnotationStatistics<>();
                for (Map<String, AnnotationStatistics<String>> foldStats : stats) {
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

            macro_f1 /= N2C2Constants.REL_TYPES.length;
            System.out.println("Macro-f1: " + macro_f1);
            System.out.println("Micro-f1: " + micro_f1);
        }
    }
}
