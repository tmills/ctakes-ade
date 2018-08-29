package org.apache.ctakes.deepphe.ae;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.ctakes.deepphe.clingen.types.textsem.*;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.cleartk.util.ViewUriUtil;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClinGenReader extends JCasAnnotator_ImplBase {
    private static Logger LOGGER = UIMAFramework.getLogger();

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        String textFn = ViewUriUtil.getURI(jCas).getPath();
        File xmlFile = new File(textFn + ".seighe.seighe.completed.xml");

        if (!xmlFile.exists()) {
            LOGGER.log(Level.WARNING, "Annotation file does not exist for this text file: " + textFn);
            return;
        }

        if (xmlFile != null) {
            processXmlFile(jCas, xmlFile);
        }
    }

    private static void processXmlFile(JCas jCas, File xmlFile) throws AnalysisEngineProcessException {
        LOGGER.log(Level.INFO, "Processing xml file: " + xmlFile.getName());
        // load the XML
        Element dataElem;
        try {
            dataElem = new SAXBuilder().build(xmlFile.toURI().toURL()).getRootElement();
        } catch (MalformedURLException e) {
            throw new AnalysisEngineProcessException(e);
        } catch (JDOMException e) {
            throw new AnalysisEngineProcessException(e);
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        int docLen = jCas.getDocumentText().length();

        for (Element annotationsElem : dataElem.getChildren("annotations")) {

            Map<String, Annotation> idToAnnotation = Maps.newHashMap();
            for (Element entityElem : annotationsElem.getChildren("entity")) {
                String id = removeSingleChildText(entityElem, "id", null);
                Element spanElem = removeSingleChild(entityElem, "span", id);
                String type = removeSingleChildText(entityElem, "type", id);
                Element propertiesElem = removeSingleChild(entityElem, "properties", id);

                // UIMA doesn't support disjoint spans, so take the span enclosing
                // everything
                int begin = Integer.MAX_VALUE;
                int end = Integer.MIN_VALUE;
                for (String spanString : spanElem.getText().split(";")) {
                    String[] beginEndStrings = spanString.split(",");
                    if (beginEndStrings.length != 2) {
                        error("span not of the format 'number,number'", id);
                    }
                    int spanBegin = Integer.parseInt(beginEndStrings[0]);
                    int spanEnd = Integer.parseInt(beginEndStrings[1]);
                    if (spanBegin < begin && spanBegin >= 0) {
                        begin = spanBegin;
                    }
                    if (spanEnd > end && spanEnd <= docLen) {
                        end = spanEnd;
                    }
                }
                if (begin < 0 || end > docLen) {
                    error("Illegal begin or end boundary", id);
                    continue;
                }

                Annotation annotation;
                if(type.equals("Gene")){
                    GeneMention gene = new GeneMention(jCas, begin, end);
                    annotation = gene;
                }else if(type.equals("Interpretation")){
                    InterpretationMention interp = new InterpretationMention(jCas, begin, end);
                    String valence = removeSingleChildText(propertiesElem, "InterpretationKind", id);
                    interp.setInterpretation(valence);
                    annotation = interp;
                }else if(type.equals("Mutation")){
                    MutationMention mutation = new MutationMention(jCas, begin, end);
                    annotation = mutation;
                }else if(type.equals("Codon")) {
                    CodonMention codon = new CodonMention(jCas, begin, end);
                    annotation = codon;
                }else if(type.equals("Syntax_P")) {
                    SyntaxPMention sp = new SyntaxPMention(jCas, begin, end);
                    annotation = sp;
                }else if(type.equals("Syntax_N")) {
                    SyntaxNMention sn = new SyntaxNMention(jCas, begin, end);
                    annotation = sn;
                } else if (type.equals("Exon")) {
                    ExonMention exon = new ExonMention(jCas, begin, end);
                    annotation = exon;
                } else if (type.equals("Amplification")) {
                    AmplificationMention amp = new AmplificationMention(jCas, begin, end);
                    annotation = amp;
                } else if (type.equals("Rearrangement")) {
                    RearrangmentMention re = new RearrangmentMention(jCas, begin, end);
                    annotation = re;
                } else if(type.equals("Chromosome")){
                    ChromosomeMention chr = new ChromosomeMention(jCas, begin, end);
                    annotation = chr;
                }else{
                    error("Unknown entity type: " + type, id);
                    throw new AnalysisEngineProcessException();
                }
                annotation.addToIndexes();
                idToAnnotation.put(id, annotation);
            }
        }
    }

    private static Element getSingleChild (Element elem, String elemName, String causeID){
        List<Element> children = elem.getChildren(elemName);
        if (children.size() != 1) {
            error(String.format("not exactly one '%s' child", elemName), causeID);
        }
        return children.size() > 0 ? children.get(0) : null;
    }

    private static Element removeSingleChild (Element elem, String elemName, String causeID){
        Element child = getSingleChild(elem, elemName, causeID);
        elem.removeChildren(elemName);
        return child;
    }

    private static String removeSingleChildText (Element elem, String elemName, String causeID){
        Element child = getSingleChild(elem, elemName, causeID);
        String text = child.getText();
        if (text.isEmpty()) {
            error(String.format("an empty '%s' child", elemName), causeID);
            text = null;
        }
        elem.removeChildren(elemName);
        return text;
    }

    private static Annotation getArgument(
            String id,
            Map<String, Annotation> idToAnnotation,
            String causeID) {
        Annotation annotation = idToAnnotation.get(id);
        if (annotation == null) {
            error("no annotation with id " + id, causeID);
        }
        return annotation;
    }

    private static void error(String found, String id) {
        LOGGER.log(Level.SEVERE, String.format("found %s in annotation with ID %s", found, id));
    }

    public static void main(String[] args) throws Exception {
        List<File> files = Lists.newArrayList();
        for (String path : args) {
            files.add(new File(path));
        }
        CollectionReader reader = UriCollectionReader.getCollectionReaderFromFiles(files);
        AnalysisEngine copier = AnalysisEngineFactory.createEngine(UriToDocumentTextAnnotator.class);
        AnalysisEngine engine = AnalysisEngineFactory.createEngine(ClinGenReader.class);

        Map<String, Integer> typeCounts = new HashMap<>();
        for(JCasIterator casIter = new JCasIterator(reader, copier, engine); casIter.hasNext(); ){
            JCas docCas = casIter.next();
            for(IdentifiedAnnotation annot : JCasUtil.select(docCas, IdentifiedAnnotation.class)){
                String entClass = annot.getClass().getSimpleName();
                if(!typeCounts.containsKey(entClass)){
                    typeCounts.put(entClass, 0);
                }
                typeCounts.put(entClass, typeCounts.get(entClass) + 1);
            }
            for(BinaryTextRelation rel : JCasUtil.select(docCas, BinaryTextRelation.class)){
                String relClass = rel.getClass().getSimpleName();
                if(!typeCounts.containsKey(relClass)){
                    typeCounts.put(relClass, 0);
                }
                typeCounts.put(relClass, typeCounts.get(relClass) + 1);
            }
        }
        typeCounts.keySet().forEach(x -> System.out.println(x + " : " + typeCounts.get(x)));
    }
}