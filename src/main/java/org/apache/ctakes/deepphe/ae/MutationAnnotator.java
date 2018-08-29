package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.MutationMention;

public class MutationAnnotator extends N2C2EntityAnnotator<MutationMention> {
    @Override
    protected Class<MutationMention> getEntityClass() {
        return MutationMention.class;
    }
}
