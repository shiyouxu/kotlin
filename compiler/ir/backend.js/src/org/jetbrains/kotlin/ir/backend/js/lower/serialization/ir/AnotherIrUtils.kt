/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir

import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.ir.SourceManager
import org.jetbrains.kotlin.ir.SourceRangeInfo
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi2ir.findFirstFunction
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import java.io.File
import java.util.regex.Pattern

internal val IrDeclaration.isAnonymousObject get() = DescriptorUtils.isAnonymousObject(this.descriptor)
internal val IrDeclaration.isLocal get() = DescriptorUtils.isLocal(this.descriptor)

internal val IrDeclaration.module get() = this.descriptor.module

@Deprecated("Do not call this method in the compiler front-end.")
internal val IrField.isDelegate get() = @Suppress("DEPRECATION") this.descriptor.isDelegated

internal const val SYNTHETIC_OFFSET = -2

class NaiveSourceBasedFileEntryImpl(override val name: String) : SourceManager.FileEntry {

    private val lineStartOffsets: IntArray

    //-------------------------------------------------------------------------//

    init {
        val file = File(name)
        if (file.isFile) {
            // TODO: could be incorrect, if file is not in system's line terminator format.
            // Maybe use (0..document.lineCount - 1)
            //                .map { document.getLineStartOffset(it) }
            //                .toIntArray()
            // as in PSI.
            val separatorLength = System.lineSeparator().length
            val buffer = mutableListOf<Int>()
            var currentOffset = 0
            file.forEachLine { line ->
                buffer.add(currentOffset)
                currentOffset += line.length + separatorLength
            }
            buffer.add(currentOffset)
            lineStartOffsets = buffer.toIntArray()
        } else {
            lineStartOffsets = IntArray(0)
        }
    }

    //-------------------------------------------------------------------------//

    override fun getLineNumber(offset: Int): Int {
        assert(offset != UNDEFINED_OFFSET)
        if (offset == SYNTHETIC_OFFSET) return 0
        val index = lineStartOffsets.binarySearch(offset)
        return if (index >= 0) index else -index - 2
    }

    //-------------------------------------------------------------------------//

    override fun getColumnNumber(offset: Int): Int {
        assert(offset != UNDEFINED_OFFSET)
        if (offset == SYNTHETIC_OFFSET) return 0
        var lineNumber = getLineNumber(offset)
        return offset - lineStartOffsets[lineNumber]
    }

    //-------------------------------------------------------------------------//

    override val maxOffset: Int
        //get() = TODO("not implemented")
        get() = UNDEFINED_OFFSET

    override fun getSourceRangeInfo(beginOffset: Int, endOffset: Int): SourceRangeInfo {
        //TODO("not implemented")
        return SourceRangeInfo(name, beginOffset, -1, -1, endOffset, -1, -1)

    }
}


val functionPattern = Pattern.compile("^(Suspend)?Function\\d+$")

internal fun isBuiltInFunction(value: IrDeclaration):Boolean = when (value) {
    is IrSimpleFunction -> value.name.asString() == "invoke" && (value.parent as? IrClass)?.let { isBuiltInFunction(it) } == true
    is IrClass -> {
        val fqn = value.parent.fqNameSafe
        fqn == FqName("kotlin") && value.name.asString().let { functionPattern.matcher(it).find() }
    }
    else -> false
}

internal fun builtInFunctionId(value: IrDeclaration): Long = when (value) {
    is IrSimpleFunction -> {
        value.run { valueParameters.size + if (isSuspend) 256 else 0 }.toLong()
    }
    is IrClass -> {
        256 * 2 + builtInFunctionId(value.declarations.first { it.name.asString() == "invoke" })
    }
    else -> error("Only class or function is expected")
}

internal fun isBuiltInFunction(value: DeclarationDescriptor):Boolean = when (value) {
    is FunctionInvokeDescriptor -> isBuiltInFunction(value.containingDeclaration)
    is ClassDescriptor -> {
        val fqn = (value.containingDeclaration as? PackageFragmentDescriptor)?.fqName
        fqn == FqName("kotlin") && value.name.asString().let { functionPattern.matcher(it).find() }
    }
    else -> false
}

internal fun builtInFunctionId(value: DeclarationDescriptor): Long = when (value) {
    is FunctionInvokeDescriptor -> {
        value.run { valueParameters.size + if (isSuspend) 256 else 0 }.toLong()
    }
    is ClassDescriptor -> {
        256 * 2 + builtInFunctionId(value.findFirstFunction("invoke") { true })
    }
    else -> error("Only class or function is expected")
}