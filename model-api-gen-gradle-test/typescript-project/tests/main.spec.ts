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

import { assert } from "chai";
import {org, jetbrains, TypeConversions} from "@modelix/api-gen-test-kotlin-project";
import ModelFacade = org.modelix.model.ModelFacade;
import L_org_modelix_model_repositoryconcepts = org.modelix.model.repositoryconcepts.L_org_modelix_model_repositoryconcepts

describe("Basic API tests", () => {
    it("sandbox", () => {
        let arrayProxy = new Proxy([0, 1, 2], {
            get(target: [number, number, number], key: string | symbol, receiver: any): any {
                console.log(receiver)
                return target[key as any]
            },
            set(target: [number, number, number], key: string | symbol, newValue: any, receiver: any): boolean {
                console.log(receiver)
                target[key as any] = newValue
                return true
            }
        })

        let l: Array<String> = TypeConversions.listToArray(null)
        let rootNode = ModelFacade.loadModelsFromJson([])
        let repoConcept: org.modelix.model.repositoryconcepts.C_Repository<org.modelix.model.repositoryconcepts.Repository> = L_org_modelix_model_repositoryconcepts.Repository;
        let repo: org.modelix.model.repositoryconcepts.Repository = org.modelix.metamodel.typed(rootNode, repoConcept)
        repo.modules.models.asArray()

        arrayProxy.push(10);
        console.log(arrayProxy)

        const result = 5;
        assert.equal(result, 5);
    });
});