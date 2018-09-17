package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityBioAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.CodonMention;

public class CodonAnnotator extends N2C2EntityBioAnnotator<CodonMention> {
    @Override
    protected Class<CodonMention> getEntityClass() {
        return CodonMention.class;
    }
}
