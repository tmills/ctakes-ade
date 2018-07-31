package org.apache.ctakes.ade.ae.entity;

import org.apache.ctakes.typesystem.type.textsem.MedicationRouteModifier;

public class MedicationRouteAnnotator extends N2C2EntityAnnotator<MedicationRouteModifier> {
    @Override
    protected Class<MedicationRouteModifier> getEntityClass() {
        return MedicationRouteModifier.class;
    }
}
