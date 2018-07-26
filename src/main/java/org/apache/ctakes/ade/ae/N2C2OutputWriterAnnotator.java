package org.apache.ctakes.ade.ae;

import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.ctakes.ade.type.entity.*;
import org.apache.ctakes.ade.type.relation.*;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.util.ViewUriUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class N2C2OutputWriterAnnotator extends JCasAnnotator_ImplBase {

    public static final String PARAM_OUT_DIR = "OutDir";
    @ConfigurationParameter(name = PARAM_OUT_DIR)
    private File outputDir=null;

    PrintWriter out = null;
    Map<String, String> relClassToN2c2Name = new HashMap<>();

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        relClassToN2c2Name.put(AdeDrugTextRelation.class.getSimpleName(), "ADE-Drug");
        relClassToN2c2Name.put(MedicationDosageTextRelation.class.getSimpleName(), "Dosage-Drug");
        relClassToN2c2Name.put(MedicationDurationTextRelation.class.getSimpleName(), "Duration-Drug");
        relClassToN2c2Name.put(MedicationFormTextRelation.class.getSimpleName(), "Form-Drug");
        relClassToN2c2Name.put(MedicationFrequencyTextRelation.class.getSimpleName(), "Frequency-Drug");
        relClassToN2c2Name.put(MedicationReasonTextRelation.class.getSimpleName(), "Reason-Drug");
        relClassToN2c2Name.put(MedicationRouteTextRelation.class.getSimpleName(), "Route-Drug");
        relClassToN2c2Name.put(MedicationStrengthTextRelation.class.getSimpleName(), "Strength-Drug");
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        List<BinaryTextRelation> n2c2Relations = getRelationList(jCas,
                AdeDrugTextRelation.class,
                MedicationDosageTextRelation.class,
                MedicationDurationTextRelation.class,
                MedicationFormTextRelation.class,
                MedicationFrequencyTextRelation.class,
                MedicationReasonTextRelation.class,
                MedicationRouteTextRelation.class,
                MedicationStrengthTextRelation.class
                );

        File inputFile = new File(ViewUriUtil.getURI(jCas));
        File outputFile = new File(outputDir, inputFile.getName().replace(".txt", ".ann"));
        try {
            out = new PrintWriter(new FileWriter(outputFile));
        }catch(IOException e){
            throw new AnalysisEngineProcessException(e);
        }
        Map<Annotation,String> eventToId = getArgumentIds(n2c2Relations);
        Map<String,Annotation> idToEvent = eventToId.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        List<String> sortedIds = new ArrayList(idToEvent.keySet());
        Collections.sort(sortedIds, new IdSorter());

        // print all events first:
        for(String id : sortedIds){
            Annotation event = idToEvent.get(id);
            out.print(eventToId.get(event));
            out.print('\t');

            // print entity type:
            if(event instanceof MedicationEventMention) out.print("Drug");
            else if(event instanceof MedicationFormModifier) out.print("Form");
            else if(event instanceof MedicationStrengthModifier) out.print("Strength");
            else if(event instanceof MedicationFrequencyModifier) out.print("Frequency");
            else if(event instanceof MedicationRouteModifier) out.print("Route");
            else if(event instanceof MedicationDosageModifier) out.print("Dosage");
            else if(event instanceof MedicationReasonMention) out.print("Reason");
            else if(event instanceof AdverseDrugEventMention) out.print("ADE");
            else if(event instanceof MedicationDurationModifier) out.print("Duration");
            else{
                System.err.println("This event has an unknown event type: " + event.getClass().getName());
                throw new AnalysisEngineProcessException();
            }
            out.print(' ');
            // print span
            out.print(event.getBegin());
            out.print(' ');
            out.print(event.getEnd());
            out.print('\t');
            // print term:
            out.print(event.getCoveredText().replace('\n', ' '));
            out.println();
        }

        // print all relations now:
        int relId=1;
        for(BinaryTextRelation rel : n2c2Relations){
            out.print("R");
            out.print(relId);
            out.print('\t');
            out.print(relClassToN2c2Name.get(rel.getClass().getSimpleName()));
            out.print(" Arg1:");
            out.print(eventToId.get(rel.getArg1().getArgument()));
            out.print(" Arg2:");
            out.print(eventToId.get(rel.getArg2().getArgument()));
            out.println();
            relId++;
        }
        out.close();
    }

    private static List<BinaryTextRelation> getRelationList(JCas jCas, Class<? extends BinaryTextRelation> ... relClasses){
        List<BinaryTextRelation> rels = new ArrayList<>();
        for(Class<? extends  BinaryTextRelation> relClass : relClasses){
            rels.addAll(JCasUtil.select(jCas, relClass));
        }
        return rels;
    }

    private Map<Annotation,String> getArgumentIds(List<BinaryTextRelation> rels){
        Map<Annotation, String> map = new HashMap<>();
        for(BinaryTextRelation rel : rels){
            Annotation arg1 = rel.getArg1().getArgument();
            Annotation arg2 = rel.getArg2().getArgument();
            for(Annotation arg : new Annotation[]{arg1, arg2}){
                if(!map.containsKey(arg)){
                    map.put(arg, "T" + (map.size()+1));
                }
            }
        }
        return map;
    }

    public static AnalysisEngineDescription getDescription(String outputDir) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(
                N2C2OutputWriterAnnotator.class,
                N2C2OutputWriterAnnotator.PARAM_OUT_DIR,
                outputDir);
    }

    private class IdSorter implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            int id1 = Integer.parseInt(o1.substring(1));
            int id2 = Integer.parseInt(o2.substring(1));
            return id1 - id2;
        }
    }
}
