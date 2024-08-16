/*
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
package org.modelix.modelql.core

import com.badoo.reaktive.observable.firstOrDefault
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.repeat
import com.badoo.reaktive.observable.zipWith
import com.badoo.reaktive.single.repeat
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.modelix.streams.firstOrNull

abstract class TransformingStepWithParameter<In : CommonIn, ParameterT : CommonIn, CommonIn, Out> : MonoTransformingStep<CommonIn, Out>() {
    private var hasStaticParameter: Boolean = false
    private var staticParameterValue: IStepOutput<ParameterT>? = null

    private var targetProducer: IProducingStep<ParameterT>? = null

    fun getInputProducer(): IProducingStep<In> = getProducer() as IProducingStep<In>
    fun getParameterProducer(): IProducingStep<ParameterT> = targetProducer!!

    override fun validate() {
        super<MonoTransformingStep>.validate()
        require(!getParameterProducer().canBeMultiple()) { "only mono parameters are supported: ${getParameterProducer()}" }
        hasStaticParameter = getParameterProducer().canEvaluateStatically()
        if (hasStaticParameter) {
            staticParameterValue = getParameterProducer().evaluateStatically().asStepOutput(null)
        }
    }

    override fun createFlow(input: StepFlow<CommonIn>, context: IFlowInstantiationContext): StepFlow<Out> {
        if (hasStaticParameter) {
            return input.map { transformElement(it.upcast<In>(), staticParameterValue as IStepOutput<ParameterT>) }
        } else {
            val parameterFlow = context.getOrCreateFlow<ParameterT>(getParameterProducer())
            val parameterValue = parameterFlow.firstOrNull()
            return input.zipWith(parameterValue.repeat()) { it, param -> transformElement(it.upcast(), param?.upcast()) }
        }
    }

    protected abstract fun transformElement(input: IStepOutput<In>, parameter: IStepOutput<ParameterT>?): IStepOutput<Out>

    override fun getProducers(): List<IProducingStep<CommonIn>> {
        return super.getProducers() + listOfNotNull<IProducingStep<CommonIn>>(targetProducer)
    }

    override fun addProducer(producer: IProducingStep<CommonIn>) {
        if (getProducers().isEmpty()) {
            super.addProducer(producer)
        } else {
            targetProducer = producer as IProducingStep<ParameterT>
        }
    }
}
