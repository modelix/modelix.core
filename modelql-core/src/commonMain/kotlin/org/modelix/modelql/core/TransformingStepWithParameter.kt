package org.modelix.modelql.core

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.single

abstract class TransformingStepWithParameter<In : CommonIn, ParameterT : CommonIn, CommonIn, Out> : MonoTransformingStep<CommonIn, Out>() {
    private var hasStaticParameter: Boolean = false
    private var staticParameterValue: ParameterT? = null

    private var targetProducer: IProducingStep<ParameterT>? = null

    fun getInputProducer(): IProducingStep<In> = getProducer() as IProducingStep<In>
    fun getParameterProducer(): IProducingStep<ParameterT> = targetProducer!!

    override fun validate() {
        super<MonoTransformingStep>.validate()
        require(!getParameterProducer().canBeMultiple()) { "only mono parameters are supported: ${getParameterProducer()}" }
        hasStaticParameter = getParameterProducer().canEvaluateStatically()
        if (hasStaticParameter) {
            staticParameterValue = getParameterProducer().evaluateStatically()
        }
    }

    override fun createFlow(input: StepFlow<CommonIn>, context: IFlowInstantiationContext): StepFlow<Out> {
        if (hasStaticParameter) {
            return input.map { SimpleStepOutput(transformElement(it.value as In, staticParameterValue)) }
        } else {
            val parameterFlow = context.getOrCreateFlow<ParameterT?>(getParameterProducer())
                .onEmpty { emit(SimpleStepOutput(null)) }
            return flow {
                val parameterValue = parameterFlow.single()
                emitAll(input.map { SimpleStepOutput(transformElement(it.value as In, parameterValue.value)) })
            }
        }
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        val parameterValue = if (hasStaticParameter) staticParameterValue else getParameterProducer().evaluate(queryInput).getOrElse(null)
        return getInputProducer().createSequence(queryInput).map { transformElement(it, parameterValue) }
    }

    override fun evaluate(queryInput: Any?): Optional<Out> {
        val input = getInputProducer().evaluate(queryInput)
        if (!input.isPresent()) return Optional.empty()
        val parameter = getParameterProducer().evaluate(queryInput)
        return Optional.of(transformElement(input.get(), parameter.getOrElse(null)))
    }

    override fun transform(input: CommonIn): Out {
        throw UnsupportedOperationException()
    }

    protected abstract fun transformElement(input: In, parameter: ParameterT?): Out

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
