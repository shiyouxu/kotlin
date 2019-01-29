/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common

import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import java.io.File
import java.io.Serializable

data class IncrementalModuleEntry(
    val projectPath: String,
    val name: String,
    val buildDir: File,
    val buildHistoryFile: File
) : Serializable {
    companion object {
        private const val serialVersionUID = 0L
    }
}

class IncrementalModuleInfo(
    val projectRoot: File,
    val dirToModule: Map<File, IncrementalModuleEntry>,
    val nameToModules: Map<String, Set<IncrementalModuleEntry>>,
    val jarToClassListFile: Map<File, File>,
    // only for js
    val jarToModule: Map<File, IncrementalModuleEntry>
) : Serializable {
    fun allModulesToFiles(): Map<IncrementalModuleEntry, Set<File>> =
        HashMap<IncrementalModuleEntry, MutableSet<File>>().apply {
            for ((file, module) in dirToModule.asSequence() + jarToModule.asSequence()) {
                getOrPut(module) { HashSet() }.add(file)
            }
        }

    companion object {
        private const val serialVersionUID = 0L
    }
}