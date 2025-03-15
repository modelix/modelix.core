package org.modelix.modelql.core

import org.modelix.streams.IStream

abstract class TransformingStepWithParameter<In : CommonIn, ParameterT : CommonIn, CommonIn, Out> : TransformingStepWithParameterBase<In, ParameterT, CommonIn, Out>() {
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()
    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    fun connectAndDowncast(producer: IMonoStep<In>): IMonoStep<Out> = also { producer.connect(it) }
    fun connectAndDowncast(producer: IFluxStep<In>): IFluxStep<Out> = also { producer.connect(it) }

    override fun transformElementToMultiple(input: IStepOutput<In>, parameter: IStepOutput<ParameterT>?): IStream.Many<IStepOutput<Out>> = IStream.of(transformElement(input, parameter))
    protected abstract fun transformElement(input: IStepOutput<In>, parameter: IStepOutput<ParameterT>?): IStepOutput<Out>
}

abstract class FluxTransformingStepWithParameter<In : CommonIn, ParameterT : CommonIn, CommonIn, Out> : TransformingStepWithParameterBase<In, ParameterT, CommonIn, Out>() {
    fun connectAndDowncast(producer: IProducingStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class TransformingStepWithParameterBase<In : CommonIn, ParameterT : CommonIn, CommonIn, Out> : TransformingStep<CommonIn, Out>(), IMonoStep<Out>, IFluxStep<Out> {
    private var hasStaticParameter: Boolean = false
    private var staticParameterValue: IStepOutput<ParameterT>? = null

    private var targetProducer: IProducingStep<ParameterT>? = null

    fun getInputProducer(): IProducingStep<In> = getProducer() as IProducingStep<In>
    fun getParameterProducer(): IProducingStep<ParameterT> = targetProducer!!

    override fun validate() {
        super<TransformingStep>.validate()
        require(!getParameterProducer().canBeMultiple()) { "only mono parameters are supported: ${getParameterProducer()}" }
        hasStaticParameter = getParameterProducer().canEvaluateStatically()
        if (hasStaticParameter) {
            staticParameterValue = getParameterProducer().evaluateStatically().asStepOutput(null)
        }
    }

    override fun createStream(input: StepStream<CommonIn>, context: IStreamInstantiationContext): StepStream<Out> {
        if (hasStaticParameter) {
            return input.flatMap { transformElementToMultiple(it.upcast<In>(), staticParameterValue as IStepOutput<ParameterT>) }
        } else {
            val parameterStream = context.getOrCreateStream<ParameterT>(getParameterProducer())
            val parameterValue = parameterStream.firstOrNull()
            return listOf(input, parameterValue).zipRepeating().flatMap {
                transformElementToMultiple(it[0]!!.upcast(), it[1]?.upcast())
            }
        }
    }

    protected abstract fun transformElementToMultiple(input: IStepOutput<In>, parameter: IStepOutput<ParameterT>?): IStream.Many<IStepOutput<Out>>

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
