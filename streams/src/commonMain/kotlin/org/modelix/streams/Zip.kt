package org.modelix.streams

fun <T1, T2, T3, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    mapper: (T1, T2, T3) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
    )
}

fun <T1, T2, T3, T4, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    s4: IStream.One<T4>,
    mapper: (T1, T2, T3, T4) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3, s4)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
    )
}

fun <T1, T2, T3, T4, T5, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    s4: IStream.One<T4>,
    s5: IStream.One<T5>,
    mapper: (T1, T2, T3, T4, T5) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3, s4, s5)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
    )
}

fun <T1, T2, T3, T4, T5, T6, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    s4: IStream.One<T4>,
    s5: IStream.One<T5>,
    s6: IStream.One<T6>,
    mapper: (T1, T2, T3, T4, T5, T6) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3, s4, s5, s6)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    s4: IStream.One<T4>,
    s5: IStream.One<T5>,
    s6: IStream.One<T6>,
    s7: IStream.One<T7>,
    mapper: (T1, T2, T3, T4, T5, T6, T7) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3, s4, s5, s6, s7)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
        values[6] as T7,
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    s4: IStream.One<T4>,
    s5: IStream.One<T5>,
    s6: IStream.One<T6>,
    s7: IStream.One<T7>,
    s8: IStream.One<T8>,
    mapper: (T1, T2, T3, T4, T5, T6, T7, T8) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3, s4, s5, s6, s7, s8)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
        values[6] as T7,
        values[7] as T8,
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> IStreamBuilder.zip(
    s1: IStream.One<T1>,
    s2: IStream.One<T2>,
    s3: IStream.One<T3>,
    s4: IStream.One<T4>,
    s5: IStream.One<T5>,
    s6: IStream.One<T6>,
    s7: IStream.One<T7>,
    s8: IStream.One<T8>,
    s9: IStream.One<T9>,
    mapper: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R,
): IStream.One<R> = IStream.zip(listOf(s1, s2, s3, s4, s5, s6, s7, s8, s9)) { values ->
    @Suppress("UNCHECKED_CAST")
    mapper(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
        values[6] as T7,
        values[7] as T8,
        values[8] as T9,
    )
}
