import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.extra.math.sumAsInt
import kotlin.test.Test

class RxSandbox {
    @Test
    fun test() {
        (1..10).toFlux().sumAsInt().subscribe { println(it) }
        val concatWith = (1..10).toFlux().concatWith(Mono.just(2))
        concatWith.subscribe { println(it) }
    }
}