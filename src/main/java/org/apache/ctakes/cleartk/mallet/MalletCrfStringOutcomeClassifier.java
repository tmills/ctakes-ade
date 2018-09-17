package org.apache.ctakes.cleartk.mallet;

import cc.mallet.fst.Transducer;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Sequence;
import org.cleartk.ml.CleartkProcessingException;
import org.cleartk.ml.Feature;
import org.cleartk.ml.encoder.CleartkEncoderException;
import org.cleartk.ml.encoder.features.FeaturesEncoder;
import org.cleartk.ml.encoder.features.NameNumber;
import org.cleartk.ml.encoder.outcome.OutcomeEncoder;
import org.cleartk.ml.jar.SequenceClassifier_ImplBase;
import org.cleartk.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * This classifier provides an interface to the <a
 * href="http://mallet.cs.umass.edu/index.php/SimpleTagger_example"> Mallet Conditional Random Field
 * (CRF) tagger</a>. Annotators that use a sequence learner such as this one will need to support
 * classification of a sequence of instances.
 *
 * <br>
 * Copyright (c) 2007-2008, Regents of the University of Colorado <br>
 * All rights reserved.
 *
 * @author Philip Ogren
 */
public class MalletCrfStringOutcomeClassifier  extends
        SequenceClassifier_ImplBase<List<NameNumber>, String, String> {
    protected Transducer transducer;

    public MalletCrfStringOutcomeClassifier(
            FeaturesEncoder<List<NameNumber>> featuresEncoder,
            OutcomeEncoder<String, String> outcomeEncoder,
            Transducer transducer) {
        super(featuresEncoder, outcomeEncoder);
        this.transducer = transducer;
    }

    /**
     * This method classifies several instances at once
     *
     * @param features
     *          a list of lists of features - each list in the list represents one instance to be
     *          classified. The list should correspond to some logical sequence of instances to be
     *          classified (e.g. tokens in a sentence or lines in a document) that corresponds to the
     *          model that has been built for this classifier.
     */
    public List<String> classify(List<List<Feature>> features) throws CleartkProcessingException {
        String[][] featureStringArray = toStrings(features);
        Pipe pipe = transducer.getInputPipe();

        Instance instance = new Instance(featureStringArray, null, null, null);
        instance = pipe.instanceFrom(instance);

        Sequence<?> data = (Sequence<?>) instance.getData();
        Sequence<?> untypedSequence = transducer.transduce(data);
        Sequence<String> sequence = ReflectionUtil.uncheckedCast(untypedSequence);

        List<String> returnValues = new ArrayList<String>();

        for (int i = 0; i < sequence.size(); i++) {
            String encodedOutcome = sequence.get(i);
            returnValues.add(outcomeEncoder.decode(encodedOutcome));
        }
        return returnValues;
    }

    /**
     * Converts the features into a 2D string array that Mallet can use. The only thing mildly tricky
     * about this method is that the length of each string array is one more than the size of each
     * feature list - i.e. <code>returnValues[0].length == features.get(0).size() + 1</code> where the
     * last element in each string array is an empty string.
     *
     * @param features
     *          the features to be converted.
     * @return a 2D string array that Mallet can use.
     */
    private String[][] toStrings(List<List<Feature>> features) throws CleartkEncoderException {
        List<List<String>> encodedFeatures = new ArrayList<List<String>>(features.size());
        for (List<Feature> features1 : features) {
            List<NameNumber> nameNumbers = this.featuresEncoder.encodeAll(features1);
            List<String> encodedFeatures1 = new ArrayList<String>();
            for (NameNumber nameNumber : nameNumbers) {
                encodedFeatures1.add(nameNumber.name);
            }
            encodedFeatures.add(encodedFeatures1);
        }

        String[][] encodedFeaturesArray = new String[encodedFeatures.size()][];
        for (int i = 0; i < encodedFeatures.size(); i++) {
            String[] encodedFeaturesArray1 = encodedFeatures.get(i).toArray(new String[0]);
            encodedFeaturesArray[i] = encodedFeaturesArray1;
        }

        return encodedFeaturesArray;
    }
}
