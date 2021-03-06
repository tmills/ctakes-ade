package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityBioAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.SyntaxPMention;

public class SyntaxPAnnotator extends N2C2EntityBioAnnotator<SyntaxPMention> {
    @Override
    protected Class<SyntaxPMention> getEntityClass() {
        return SyntaxPMention.class;
    }
}
