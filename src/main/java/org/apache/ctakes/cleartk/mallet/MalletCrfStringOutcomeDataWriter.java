package org.apache.ctakes.cleartk.mallet;

import org.cleartk.ml.encoder.features.*;
import org.cleartk.ml.encoder.outcome.StringToStringOutcomeEncoder;
import org.cleartk.ml.jar.SequenceDataWriter_ImplBase;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * <br>
 * Copyright (c) 2007-2008, Regents of the University of Colorado <br>
 * All rights reserved.
 *
 *
 * This training data consumer produces training data suitable for <a
 * href="http://mallet.cs.umass.edu/index.php/SimpleTagger_example"> Mallet Conditional Random Field
 * (CRF) tagger</a>.
 *
 * Each line of the training data contains a string representation of each feature followed by the
 * label/result for that instance.
 *
 * @author Philip Ogren
 * @author Steven Bethard
 */
public class MalletCrfStringOutcomeDataWriter extends
        SequenceDataWriter_ImplBase<MalletCrfStringOutcomeClassifierBuilder, List<NameNumber>, String, String> {
    public MalletCrfStringOutcomeDataWriter(File outputDirectory) throws IOException {
        super(outputDirectory);
        NameNumberFeaturesEncoder fe = new NameNumberFeaturesEncoder();
        fe.addEncoder(new NumberEncoder());
        fe.addEncoder(new BooleanEncoder());
        fe.addEncoder(new StringEncoder());
        this.setFeaturesEncoder(fe);
        this.setOutcomeEncoder(new StringToStringOutcomeEncoder());
    }

    @Override
    public void writeEncoded(List<NameNumber> features, String outcome) {
        for (NameNumber nameNumber : features) {
            this.trainingDataWriter.print(nameNumber.name);
            this.trainingDataWriter.print(" ");
        }

        this.trainingDataWriter.print(outcome);
        this.trainingDataWriter.println();
    }

    @Override
    public void writeEndSequence() {
        this.trainingDataWriter.println();
    }

    @Override
    protected MalletCrfStringOutcomeClassifierBuilder newClassifierBuilder() {
        return new MalletCrfStringOutcomeClassifierBuilder();
    }
}
