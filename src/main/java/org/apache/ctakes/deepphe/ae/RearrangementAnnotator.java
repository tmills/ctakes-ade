package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.RearrangmentMention;

public class RearrangementAnnotator extends N2C2EntityAnnotator<RearrangmentMention> {
    @Override
    protected Class<RearrangmentMention> getEntityClass() {
        return RearrangmentMention.class;
    }
}
