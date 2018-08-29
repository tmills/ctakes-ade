package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.GeneMention;

public class GeneAnnotator extends N2C2EntityAnnotator<GeneMention> {
    @Override
    protected Class<GeneMention> getEntityClass() {
        return GeneMention.class;
    }
}
