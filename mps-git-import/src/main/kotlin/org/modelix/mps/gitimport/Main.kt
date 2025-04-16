package org.modelix.mps.gitimport

import com.github.ajalt.clikt.core.main

fun runFromAnt() {
    val args = sequence {
        var i = 0
        while (true) {
            yield(System.getProperty("modelix.git.import.args.$i") ?: return@sequence)
            i++
        }
    }.toList()

    ModelixSyncCommand().main(args)
}
