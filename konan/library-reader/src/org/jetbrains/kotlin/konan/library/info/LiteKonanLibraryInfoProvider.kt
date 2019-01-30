/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.library.info

import org.jetbrains.kotlin.konan.library.KLIB_MANIFEST_FILE_NAME
import org.jetbrains.kotlin.konan.library.KLIB_PROPERTY_UNIQUE_NAME
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

object LiteKonanLibraryInfoProvider {

    private val kotlinNativeDataDir by lazy {
        val path = System.getenv("KONAN_DATA_DIR")?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".konan")
        path.toAbsolutePath()
    }

    /**
     * Returns either [LiteKonanLibrary], or null if there is no such library in
     * Kotlin/Native distribution.
     */
    fun getDistributionLibraryInfo(rawPath: String): LiteKonanLibrary? {
        val libraryPath = Paths.get(rawPath)

        // check whether it under Kotlin/Native root
        if (!libraryPath.startsWith(kotlinNativeDataDir))
            return null

        val manifestFile = libraryPath.resolve(KLIB_MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestFile))
            return null

        val parentPath = libraryPath.parent ?: return null
        val parentName = parentPath.toFile().name

        val platform = when (parentName) {
            "common" -> null
            else -> {
                val grandParentName = parentPath.parent?.toFile()?.name ?: return null
                when (grandParentName) {
                    "platform" -> parentName
                    else -> return null
                }
            }
        }

        val manifestProperties = Properties().apply {
            try {
                Files.newInputStream(manifestFile).use { load(it) }
            } catch (e: IOException) {
                return null
            }
        }

        val name = manifestProperties[KLIB_PROPERTY_UNIQUE_NAME]?.toString() ?: return null

        return LiteKonanLibrary(libraryPath, name, platform)
    }
}
