/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
