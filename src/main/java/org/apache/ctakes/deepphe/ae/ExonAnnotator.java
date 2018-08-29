package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.ExonMention;

public class ExonAnnotator extends N2C2EntityAnnotator<ExonMention> {
    @Override
    protected Class<ExonMention> getEntityClass() {
        return ExonMention.class;
    }
}
