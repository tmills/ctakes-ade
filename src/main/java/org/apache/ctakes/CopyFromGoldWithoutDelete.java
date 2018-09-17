package org.apache.ctakes;

import com.google.common.collect.Lists;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;

public class CopyFromGoldWithoutDelete extends JCasAnnotator_ImplBase {
    public static AnalysisEngineDescription getDescription(String goldViewName, Class<?>... classes )
            throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(
                CopyFromGoldWithoutDelete.class,
                CopyFromGoldWithoutDelete.PARAM_GOLD_VIEW_NAME,
                goldViewName,
                CopyFromGoldWithoutDelete.PARAM_ANNOTATION_CLASSES,
                classes );
    }

    public static final String PARAM_ANNOTATION_CLASSES = "AnnotationClasses";

    @ConfigurationParameter( name = PARAM_ANNOTATION_CLASSES, mandatory = true )
    private Class<? extends TOP>[] annotationClasses;

    public static final String PARAM_GOLD_VIEW_NAME = "GoldViewName";
    @ConfigurationParameter( name = PARAM_GOLD_VIEW_NAME, mandatory = true )
    private String goldViewName;

    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        JCas goldView, systemView;
        try {
            goldView = jCas.getView( goldViewName );
            systemView = jCas.getView( CAS.NAME_DEFAULT_SOFA );
        } catch ( CASException e ) {
            throw new AnalysisEngineProcessException( e );
        }
        CasCopier copier = new CasCopier( goldView.getCas(), systemView.getCas() );
        Feature sofaFeature = jCas.getTypeSystem().getFeatureByFullName( CAS.FEATURE_FULL_NAME_SOFA );
        for ( Class<? extends TOP> annotationClass : this.annotationClasses ) {
            for ( TOP annotation : JCasUtil.select( goldView, annotationClass ) ) {
                TOP copy = (TOP)copier.copyFs( annotation );
                if ( copy instanceof Annotation) {
                    copy.setFeatureValue( sofaFeature, systemView.getSofa() );
                }
                copy.addToIndexes( systemView );
            }
        }
    }
}
