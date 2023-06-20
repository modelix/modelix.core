package org.modelix.modelql.untyped

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEmpty
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.MonoTransformingStep

abstract class TransformingStepWithParameter<In : CommonIn, ParameterT : CommonIn, CommonIn, Out> : MonoTransformingStep<CommonIn, Out>() {
    private var targetProducer: IProducingStep<ParameterT>? = null

    fun getInputProducer(): IProducingStep<In> = getProducer() as IProducingStep<In>
    fun getParameterProducer(): IProducingStep<ParameterT> = targetProducer!!

    override fun createFlow(input: Flow<CommonIn>, context: IFlowInstantiationContext): Flow<Out> {
        val parameterFlow = context.getOrCreateFlow<ParameterT?>(getParameterProducer()).onEmpty { emit(null) }
        return input.combine(parameterFlow) { inputElement, parameter ->
            transformElement(inputElement as In, parameter as ParameterT)
        }
    }

    protected abstract fun transformElement(input: In, parameter: ParameterT): Out

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
