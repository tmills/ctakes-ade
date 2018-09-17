package org.apache.ctakes.deepphe.ae;

import org.apache.ctakes.ade.ae.entity.N2C2EntityBioAnnotator;
import org.apache.ctakes.deepphe.clingen.types.textsem.SyntaxNMention;

public class SyntaxNAnnotator extends N2C2EntityBioAnnotator<SyntaxNMention> {
    @Override
    protected Class<SyntaxNMention> getEntityClass() {
        return SyntaxNMention.class;
    }
}
