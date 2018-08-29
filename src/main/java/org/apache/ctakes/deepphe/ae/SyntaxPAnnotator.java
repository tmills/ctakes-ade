package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.SyntaxPMention;

public class SyntaxPAnnotator extends N2C2EntityAnnotator<SyntaxPMention> {
    @Override
    protected Class<SyntaxPMention> getEntityClass() {
        return SyntaxPMention.class;
    }
}
