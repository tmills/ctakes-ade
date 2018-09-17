package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityBioAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.InterpretationMention;

public class InterpretationAnnotator extends N2C2EntityBioAnnotator<InterpretationMention> {
    @Override
    protected Class<InterpretationMention> getEntityClass() {
        return InterpretationMention.class;
    }

    @Override
    protected String getChunkFeatureName() {
        return "interpretation";
    }
}
