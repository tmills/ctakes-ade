package org.apache.ctakes.deepphe.eval;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;
import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.ade.type.relation.*;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.*;
import org.apache.ctakes.deepphe.ClingenConstants;
import org.apache.ctakes.deepphe.ae.*;
import org.apache.ctakes.deepphe.clingen.types.textsem.*;
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

public class EvaluateClingen extends Evaluation_ImplBase<File,Map<String,AnnotationStatistics<String>>> {

    public static final String GOLD_VIEW_NAME = "GoldView";
    enum RUN_MODE {JOINT, ENT, RELS};

    private static final Logger logger = UIMAFramework.getLogger(EvaluateClingen.class);

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
    public EvaluateClingen(File baseDirectory, File outputDir, RUN_MODE runMode) {
        super(baseDirectory);
        relStringToRelation = new HashMap<>();
        // TODO: Fill in once we have relations we want to extract
        entStringToRelation = new HashMap<>();
        entStringToRelation.put(ClingenConstants.CODON_DIR, CodonMention.class);
        entStringToRelation.put(ClingenConstants.EXON_DIR, ExonMention.class);
        entStringToRelation.put(ClingenConstants.GENE_DIR, GeneMention.class);
        entStringToRelation.put(ClingenConstants.INTERPRETATION_DIR, InterpretationMention.class);
        entStringToRelation.put(ClingenConstants.MUTATION_DIR, MutationMention.class);
        entStringToRelation.put(ClingenConstants.REARRANGMENT_DIR, RearrangmentMention.class);
        entStringToRelation.put(ClingenConstants.SYNTAX_N_DIR, SyntaxNMention.class);
        entStringToRelation.put(ClingenConstants.SYNTAX_P_DIR, SyntaxPMention.class);

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
                    CodonMention.class,
                    ExonMention.class,
                    GeneMention.class,
                    InterpretationMention.class,
                    MutationMention.class,
                    RearrangmentMention.class,
                    SyntaxNMention.class,
                    SyntaxPMention.class
                    ));
            // if we are doing only rels or only ents, then don't bother doing model-building for the other:
            if(this.runMode == RUN_MODE.ENT || this.runMode == RUN_MODE.JOINT) {
                builder.add(getEntityDataWriters(directory));
            }
            if(this.runMode == RUN_MODE.RELS || this.runMode == RUN_MODE.JOINT) {
                builder.add(getRelationDataWriters(directory, (float) this.downsample));
            }
            boolean goldArgs = (this.runMode == RUN_MODE.RELS);

            // Put a loop here over relation types and their sub-directories
            SimplePipeline.runPipeline(collectionReader, builder.createAggregate());
        }

        // train entity classifiers:
        if(this.runMode != RUN_MODE.RELS) {
            for (String ent : ClingenConstants.ENT_TYPES) {
                JarClassifierBuilder.trainAndPackage(new File(directory, ent), new String[]{"-s", "1", "-c", "0.1"});
            }
        }

        // train relation classifiers:
        if(this.runMode != RUN_MODE.ENT) {
            for (String rel : ClingenConstants.REL_TYPES) {
                JarClassifierBuilder.trainAndPackage(new File(directory, rel), new String[]{"-s", "1", "-c", "0.1"});
            }
        }
    }

    @Override
    protected Map<String,AnnotationStatistics<String>> test(CollectionReader collectionReader, File directory) throws Exception {
        Map<String,AnnotationStatistics<String>> stats = new HashMap<>();
        for(String entType : ClingenConstants.ENT_TYPES){
            stats.put(entType, new AnnotationStatistics<>());
        }
        for(String relType : ClingenConstants.REL_TYPES){
            stats.put(relType, new AnnotationStatistics<>());
        }
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(AnalysisEngineFactory.createEngineDescription(SHARPXMI.DocumentIDAnnotator.class));
        builder.add(AnalysisEngineFactory.createEngineDescription(DocumentIdPrinterAnalysisEngine.class));

        if(this.runMode == RUN_MODE.RELS) {
            // For rel-only evaluation we assume gold standard entity inputs:
            builder.add(CopyFromGold.getDescription(GOLD_VIEW_NAME,
                    GeneMention.class
            ));
        }else{
            // for ent-only or joint evaluation we need to add entity annotators:
            builder.add(getEntityAnnotators(directory));
        }
        if(this.runMode != RUN_MODE.ENT) {
            builder.add(getRelationAnnotators(directory));
        }

        // Functions needed to evaluate the relations:
        Function<BinaryTextRelation, RelationExtractorEvaluation.HashableArguments> getSpan =
                relation -> new RelationExtractorEvaluation.HashableArguments(relation);
        Function<BinaryTextRelation, String> getOutcome =
                AnnotationStatistics.annotationToFeatureValue("category");

        JCasIterator casIter = new JCasIterator(collectionReader, builder.createAggregate());
        while(casIter.hasNext()){
            JCas jCas = casIter.next();
            JCas goldView = jCas.getView(GOLD_VIEW_NAME);

            for(String entType : ClingenConstants.ENT_TYPES) {
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
            for(String relType : ClingenConstants.REL_TYPES) {
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
            File xmiFile = new File(xmiDir, inputFile.getName() + ".xmi");
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
            builder.add(AnalysisEngineFactory.createEngineDescription(ViewCreatorAnnotator.class,
                    ViewCreatorAnnotator.PARAM_VIEW_NAME,
                    GOLD_VIEW_NAME));
            builder.add(UriToDocumentTextAnnotator.getDescription());
            builder.add(UriToDocumentTextAnnotator.getDescription(), CAS.NAME_DEFAULT_SOFA, GOLD_VIEW_NAME);
            builder.add(getPreprocessingPipeline());
            builder.add(AnalysisEngineFactory.createEngineDescription(ClinGenReader.class), CAS.NAME_DEFAULT_SOFA, GOLD_VIEW_NAME);

            for (JCasIterator casIter = new JCasIterator(reader, builder.createAggregate()); casIter.hasNext(); ) {
                JCas jCas = casIter.next();
                File inputFile = new File(ViewUriUtil.getURI(jCas));
                File outputFile = new File(xmiDir, inputFile.getName() + ".xmi");

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

        builder.add(N2C2EntityAnnotator.getDataWriterDescription(CodonAnnotator.class, new File(directory, ClingenConstants.CODON_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(ExonAnnotator.class, new File(directory, ClingenConstants.EXON_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(GeneAnnotator.class, new File(directory, ClingenConstants.GENE_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(InterpretationAnnotator.class, new File(directory, ClingenConstants.INTERPRETATION_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(MutationAnnotator.class, new File(directory, ClingenConstants.MUTATION_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(RearrangementAnnotator.class, new File(directory, ClingenConstants.REARRANGMENT_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(SyntaxNAnnotator.class, new File(directory, ClingenConstants.SYNTAX_N_DIR)));
        builder.add(N2C2EntityAnnotator.getDataWriterDescription(SyntaxPAnnotator.class, new File(directory, ClingenConstants.SYNTAX_P_DIR)));
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getRelationDataWriters(File directory, float downsample) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();
//        builder.add(N2C2RelationAnnotator.getDataWriterDescription(MedicationStrengthRelationAnnotator.class, new File(directory, N2C2Constants.DRUG_STRENGTH_DIR), downsample));
        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getEntityAnnotators(File directory) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(N2C2EntityAnnotator.getClassifierDescription(CodonAnnotator.class, new File(directory, ClingenConstants.CODON_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(ExonAnnotator.class, new File(directory, ClingenConstants.EXON_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(GeneAnnotator.class, new File(directory, ClingenConstants.GENE_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(InterpretationAnnotator.class, new File(directory, ClingenConstants.INTERPRETATION_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(MutationAnnotator.class, new File(directory, ClingenConstants.MUTATION_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(RearrangementAnnotator.class, new File(directory, ClingenConstants.REARRANGMENT_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(SyntaxNAnnotator.class, new File(directory, ClingenConstants.SYNTAX_N_DIR)));
        builder.add(N2C2EntityAnnotator.getClassifierDescription(SyntaxPAnnotator.class, new File(directory, ClingenConstants.SYNTAX_P_DIR)));

        return builder.createAggregateDescription();
    }

    private static AnalysisEngineDescription getRelationAnnotators(File directory) throws ResourceInitializationException {
        AggregateBuilder builder = new AggregateBuilder();

//        builder.add(N2C2RelationAnnotator.getClassifierDescription(MedicationStrengthRelationAnnotator.class, new File(new File(directory, N2C2Constants.DRUG_STRENGTH_DIR), "model.jar")));

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

    private static List<File> getFilesFromDirectory(File anaforaDirectory){
        List<File> files = new ArrayList<>();
        File[] dirs = anaforaDirectory.listFiles();
        for(File dir : dirs){
            if(dir.isDirectory()){
                // anafora structure is a text file inside a directory with the same name:
                File txtFile = new File(dir, dir.getName());
                File xmlFile = new File(dir, dir.getName() + ".seighe.seighe.completed.xml");
                if(txtFile.exists() && xmlFile.exists()){
                    files.add(txtFile);
                }
            }
        }
        return files;
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
                description = "Anafora root directory")
        public File getTrainDir();

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
        RUN_MODE runMode= RUN_MODE.JOINT;
        switch(options.getRunMode()){
            case "E":
                runMode = RUN_MODE.ENT;
                break;
            case "R":
                runMode = RUN_MODE.RELS;
                System.err.println("Relation modes not yet enabled.");
                System.exit(-1);
                break;
            case "J":
                runMode = RUN_MODE.JOINT;
                System.err.println("Relation modes not yet enabled.");
                System.exit(-1);
                break;
            default:
                System.err.println("Unreocgnized option " + options.getRunMode());
        }
        EvaluateClingen eval = new EvaluateClingen(new File("target/models/clingen"), options.getOutputDir(), runMode);
        eval.skipWrite = options.getSkipWrite();
        eval.skipTrain = options.getSkipTrain();
        eval.downsample = options.getDownsample();

        List<File> trainItems = getFilesFromDirectory(options.getTrainDir());
        List<Map<String,AnnotationStatistics<String>>> stats = new ArrayList<>();

//        if(options.getEvalDir() != null){
//            List<File> evalItems = getFilesFromDirectory(options.getEvalDir());
//            List<File> trainXmi = preprocessXmi(options.getXmiDir(), trainItems);
//            List<File> evalXmi = preprocessXmi(options.getXmiDir(), evalItems);
//            stats.add(eval.trainAndTest(trainXmi, evalXmi));
//        }else{
            // run cross-validation
            List<File> trainXmi = preprocessXmi(options.getXmiDir(), trainItems);
            List<Map<String, AnnotationStatistics<String>>> allFoldsStats = eval.crossValidation(trainXmi, options.getFolds());
            stats.addAll(allFoldsStats);
//        }


        if(runMode != RUN_MODE.RELS){
            System.out.println("Entity scoring: ");
            double macro_f1 = 0;
            double tps = 0, gps = 0, preds = 0;
            for(String entType : ClingenConstants.ENT_TYPES) {
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

            macro_f1 /= ClingenConstants.ENT_TYPES.length;
            System.out.println("Macro-f1: " + macro_f1);
            System.out.println("Micro-f1: " + micro_f1);
        }
        if(runMode != RUN_MODE.ENT) {
            System.out.println("Relation scoring:");
            double macro_f1 = 0;
            double tps = 0, gps = 0, preds = 0;
            for (String relType : ClingenConstants.REL_TYPES) {
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

            macro_f1 /= ClingenConstants.REL_TYPES.length;
            System.out.println("Macro-f1: " + macro_f1);
            System.out.println("Micro-f1: " + micro_f1);
        }
    }
}
