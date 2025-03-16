package org.modelix.streams

import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.observableUnsafe
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asObservable
import org.modelix.kotlin.utils.ContextValue
import org.modelix.kotlin.utils.UnstableModelixFeature

class SynchronousPipeline {
    val afterRootSubscribed = ArrayList<() -> Unit>()

    companion object {
        val contextValue = ContextValue<SynchronousPipeline>()
    }
}

@UnstableModelixFeature("Will be removed", "never")
fun <T> Single<T>.endOfSynchronousPipeline(): Single<T> = asObservable().endOfSynchronousPipeline().exactlyOne()

fun <T> Observable<T>.endOfSynchronousPipeline(): Observable<T> {
    val upstream = this
    return observableUnsafe<T> { observer ->
        val pipeline = SynchronousPipeline()
        SynchronousPipeline.contextValue.computeWith(pipeline) {
            upstream.subscribe(observer)
            pipeline.afterRootSubscribed.toList().forEach { it() }
        }
    }
}
