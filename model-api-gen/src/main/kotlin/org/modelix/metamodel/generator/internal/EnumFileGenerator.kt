/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import org.modelix.metamodel.IPropertyValueEnum
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.ProcessedEnum
import org.modelix.metamodel.generator.ProcessedEnumMember
import org.modelix.metamodel.generator.addDeprecationIfNecessary
import org.modelix.metamodel.generator.runBuild
import java.nio.file.Path

internal class EnumFileGenerator(
    private val enum: ProcessedEnum,
    override val outputDir: Path,
) : FileGenerator {

    private val enumType = ClassName(enum.language.name, enum.name)

    override fun generateFileSpec(): FileSpec {
        val generatedEnum = TypeSpec.enumBuilder(enum.name).runBuild {
            addDeprecationIfNecessary(enum)
            addPrimaryConstructor()
            addSuperinterface(IPropertyValueEnum::class)
            addEnumProperties()
            addEnumMembers()
            addCompanionObject()
        }

        return FileSpec.builder(enum.language.name, enum.name).runBuild {
            addFileComment(MetaModelGenerator.HEADER_COMMENT)
            addType(generatedEnum)
        }
    }

    private fun TypeSpec.Builder.addEnumProperties() {
        val presentation = PropertySpec.builder("presentation", String::class.asTypeName().copy(nullable = true)).runBuild {
            initializer("presentation")
            addModifiers(KModifier.OVERRIDE)
        }
        val uid = PropertySpec.builder("uid", String::class).runBuild {
            initializer("uid")
        }

        addProperty(presentation)
        addProperty(uid)
    }

    private fun TypeSpec.Builder.addEnumMembers() {
        enum.getAllMembers().forEach { addEnumMember(it) }
    }

    private fun TypeSpec.Builder.addEnumMember(member: ProcessedEnumMember) {
        val typeSpec = TypeSpec.anonymousClassBuilder().runBuild {
            addSuperclassConstructorParameter("%S", member.uid)
            addSuperclassConstructorParameter(
                if (member.presentation == null) "null" else "%S",
                member.presentation ?: "",
            )
        }
        addEnumConstant(member.name, typeSpec)
    }

    private fun TypeSpec.Builder.addCompanionObject() {
        val companion = TypeSpec.companionObjectBuilder().runBuild {
            addDefaultValueFun()
            addGetLiteralByMemberIdFun()
        }
        addType(companion)
    }

    private fun TypeSpec.Builder.addDefaultValueFun() {
        val defaultValue = FunSpec.builder("defaultValue").runBuild {
            returns(enumType)
            addCode("return values()[%L]", enum.defaultIndex)
        }
        addFunction(defaultValue)
    }

    private fun TypeSpec.Builder.addGetLiteralByMemberIdFun() {
        val body = CodeBlock.builder().runBuild {
            beginControlFlow("return when (uid) {")
            for (member in enum.getAllMembers()) {
                addStatement("%S -> %N", member.uid, member.name)
            }
            addStatement("else -> defaultValue()")
            endControlFlow()
        }

        val getLiteralMemberByMemberId = FunSpec.builder("getLiteralByMemberId").runBuild {
            returns(enumType)
            addParameter("uid", String::class)
            addCode(body)
        }
        addFunction(getLiteralMemberByMemberId)
    }

    private fun TypeSpec.Builder.addPrimaryConstructor() {
        val constructorSpec = FunSpec.constructorBuilder().runBuild {
            addParameter("uid", String::class)
            addParameter("presentation", String::class.asTypeName().copy(nullable = true))
        }
        primaryConstructor(constructorSpec)
    }
}
