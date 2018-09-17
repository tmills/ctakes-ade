package org.apache.ctakes.ade.ae;

import org.apache.ctakes.typesystem.type.constants.CONST;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.ctakes.ade.type.entity.*;
import org.apache.ctakes.ade.type.relation.*;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.cleartk.util.ViewUriUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class N2C2Reader extends JCasAnnotator_ImplBase {

    Logger logger = UIMAFramework.getLogger();

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        String textFn = ViewUriUtil.getURI(jCas).getPath();
        File annFile = new File(textFn.replace(".txt", ".ann"));
        if(!annFile.exists()){
            logger.log(Level.WARNING, "Annotation (.ann) file does not exist for this text file: " + textFn);
            return;
        }

        Map<String,Annotation> entId2Annotation = new HashMap<>();
        List<String> relationLines = new ArrayList<>();

        try(Scanner scanner = new Scanner(new FileReader(annFile))){
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.startsWith("T")) {
                    String[] pair = line.split("\\t");
                    String entId = pair[0];
                    String middle = pair[1];
                    String mentionText = pair[2];
                    String[] spans = middle.split(" ");
                    String entType = spans[0];
                    int begin, end;
                    if (spans.length == 3) {
                        // normally a span is "<begin char offset> <end char offset>"
                        begin = Integer.parseInt(spans[1]);
                        end = Integer.parseInt(spans[2]);
                    } else {
                        // but sometimes there are disjoint spans in the annotation:
                        // "<begin sub-span 1 char offset> <end sub-span 1 char offset> ... <begin sub-span n char offset> <end sub-span n char offset>"
                        begin = Integer.parseInt(spans[1]);
                        end = Integer.parseInt(spans[spans.length - 1]);
                    }

                    IdentifiedAnnotation mention = null;
                    if (entType.equals("Drug")) {
                        mention = new MedicationMention(jCas, begin, end);
                    } else if (entType.equals("Route")) {
                        mention = new MedicationRouteModifier(jCas, begin, end);
                    } else if (entType.equals("Frequency")) {
                        mention = new MedicationFrequencyModifier(jCas, begin, end);
                    } else if (entType.equals("ADE")) {
                        mention = new AdverseDrugEventMention(jCas, begin, end);
                    } else if (entType.equals("Reason")) {
                        mention = new MedicationReasonMention(jCas, begin, end);
                    } else if (entType.equals("Strength")) {
                        mention = new MedicationStrengthModifier(jCas, begin, end);
                    } else if (entType.equals("Dosage")) {
                        mention = new MedicationDosageModifier(jCas, begin, end);
                    } else if (entType.equals("Duration")) {
                        mention = new MedicationDurationModifier(jCas, begin, end);
                    } else if (entType.equals("Form")) {
                        mention = new MedicationFormModifier(jCas, begin, end);
                    } else {
                        logger.log(Level.WARNING, "Read an entity type that was not recognized: " + entType);
                    }

                    if (mention != null) {
                        mention.setDiscoveryTechnique(CONST.NE_DISCOVERY_TECH_GOLD_ANNOTATION);
                        entId2Annotation.put(entId, mention);
                        mention.addToIndexes();
                    }
                } else if (line.startsWith("R")) {
                    relationLines.add(line);
                }
            }
            for(String line : relationLines){
                // Format is:
                // RelId<tab><Rel Type> R1:<Arg1Id> R2:<Arg2Id>
                // and relations always occur after both of their arguments as far as I can see so we don't
                // need to do any delayed processing
                String[] pair = line.split("\\t");
                String relId = pair[0];
                String[] relInfo = pair[1].split(" ");
                String relType = relInfo[0];
                String arg1Id = relInfo[1].split(":")[1];
                String arg2Id = relInfo[2].split(":")[1];
                RelationArgument arg1 = new RelationArgument(jCas);
                arg1.setArgument(entId2Annotation.get(arg1Id));
                RelationArgument arg2 = new RelationArgument(jCas);
                arg2.setArgument(entId2Annotation.get(arg2Id));
                BinaryTextRelation rel = null;
                switch(relType){
                    case "ADE-Drug":
                        rel = new AdeDrugTextRelation(jCas);
                        break;
                    case "Strength-Drug":
                        rel = new MedicationStrengthTextRelation(jCas);
                        break;
                    case "Dosage-Drug":
                        rel = new MedicationDosageTextRelation(jCas);
                        break;
                    case "Route-Drug":
                        rel = new MedicationRouteTextRelation(jCas);
                        break;
                    case "Duration-Drug":
                        rel = new MedicationDurationTextRelation(jCas);
                        break;
                    case "Frequency-Drug":
                        rel = new MedicationFrequencyTextRelation(jCas);
                        break;
                    case "Form-Drug":
                        rel = new MedicationFormTextRelation(jCas);
                        break;
                    case "Reason-Drug":
                        rel = new MedicationReasonTextRelation(jCas);
                        break;
                    default:
                        logger.log(Level.WARNING, "Relation type: " + relType + " is not recognized by the reader.");
                }
                if(arg1 == null || arg2 == null || arg1.getArgument() == null || arg2.getArgument() == null){
                    logger.log(Level.WARNING, "One of the arguments to this relation is null");
                }
                if(rel != null) {
                    rel.setCategory("RelatedTo");
                    rel.setArg1(arg1);
                    rel.setArg2(arg2);
                    rel.addToIndexes();
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
