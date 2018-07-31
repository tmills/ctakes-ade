package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.ade.type.entity.AdverseDrugEventMention;

public class AdeAnnotator extends N2C2EntityAnnotator<AdverseDrugEventMention> {
    @Override
    protected Class<AdverseDrugEventMention> getEntityClass() {
        return AdverseDrugEventMention.class;
    }
}
