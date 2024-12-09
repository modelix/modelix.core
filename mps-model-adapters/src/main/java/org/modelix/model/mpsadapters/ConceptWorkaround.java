package org.modelix.model.mpsadapters;

import org.jetbrains.mps.openapi.language.SAbstractConcept;
import org.jetbrains.mps.openapi.language.SConcept;
import org.jetbrains.mps.openapi.language.SInterfaceConcept;

/**
 * The Kotlin compiler cannot disambiguate the call to getSuperConcept/getSuperInterfaces
 * that where moved to SAbstractConcept in MPS 2021.2, but still exist in SConcept/SInterfaceConcept.
 */
public class ConceptWorkaround {
    public SAbstractConcept concept;

    public ConceptWorkaround(SAbstractConcept concept) {
        this.concept = concept;
    }

    public SConcept getSuperConcept() {
        return ((SConcept) concept).getSuperConcept();
    }

    public Iterable<SInterfaceConcept> getSuperInterfaces() {
        if (concept instanceof SConcept) {
            return ((SConcept) concept).getSuperInterfaces();
        } else {
            return ((SInterfaceConcept) concept).getSuperInterfaces();
        }
    }
}
