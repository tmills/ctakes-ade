package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.SyntaxNMention;

public class SyntaxNAnnotator extends N2C2EntityAnnotator<SyntaxNMention> {
    @Override
    protected Class<SyntaxNMention> getEntityClass() {
        return SyntaxNMention.class;
    }
}
