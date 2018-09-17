package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityBioAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.ExonMention;

public class ExonAnnotator extends N2C2EntityBioAnnotator<ExonMention> {
    @Override
    protected Class<ExonMention> getEntityClass() {
        return ExonMention.class;
    }
}
