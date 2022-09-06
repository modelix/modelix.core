/*
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
package org.modelix.editor.kernelf

import de.slisson.mps.richtext.L_de_slisson_mps_richtext
import jetbrains.mps.lang.core.N_INamedConcept
import org.iets3.core.expr.base.L_org_iets3_core_expr_base
import org.iets3.core.expr.simpleTypes.L_org_iets3_core_expr_simpleTypes
import org.iets3.core.expr.tests.L_org_iets3_core_expr_tests
import org.iets3.core.expr.toplevel.L_org_iets3_core_expr_toplevel
import org.modelix.editor.languageEditors
import org.modelix.metamodel.GeneratedConcept


class KernelfEditor {


    val tests = languageEditors(L_org_iets3_core_expr_tests) {
        conceptEditor(language.TestSuite) {
            "test suite".cell()
            concept.name.cell()
            emptyLine()
            "-----------------------------------".cell()
            emptyLine()
            concept.contents.vertical()
        }
        conceptEditor(language.TestCase) {
            "test case".cell()
            concept.name.cell()
            foldable("{...}") {
                // TODO test status
                optional {
                    "setup".cell()
                    concept.setup.cell()
                }
                ifEmpty(concept.setup) {
                    newLine()
                }
                curlyBrackets {
                    concept.items.vertical()
                }
            }
        }
        conceptEditor(language.AssertTestItem) {
            optional {
                concept.optionalName.cell()
                "=".cell()
            }
            "assert".cell()
            concept.actual.cell()
            concept.strict.flagCell()
            concept.op.cell()
            concept.expected.cell()
            withNode {
                if (node.isIgnored == "true") {
                    "[ignored]".cell {
                        textColor("red")
                        backgroundColor("orange")
                    }
                }
            }

        }
        conceptEditor(language.EqualsTestOp) {
            "equals".cell()
        }
        conceptEditor(language.NoneExpr) {
            "none".cell()
            noSpace()
            angleBrackets {
                concept.expr.cell()
            }
        }
        conceptEditor(language.OptExpression) {
            "some".cell()
            noSpace()
            angleBrackets {
                concept.expr.cell()
            }
        }
    }

    val toplevel = languageEditors(L_org_iets3_core_expr_toplevel) {
        conceptEditor(language.Constant) {
            "val".cell()
            concept.name.cell()
            optional {
                ":".cell()
                concept.type.cell()
            }
            "=".cell()
            concept.value.cell()
        }
        conceptEditor(language.ConstantRef) {
            concept.constant.cell({ name })
        }
        conceptEditor(language.EmptyToplevelContent) {
            constant("")
        }
    }

    val simpleTypes = languageEditors(L_org_iets3_core_expr_simpleTypes) {
        conceptEditor(language.StringLiteral) {
            "\"".cell()
            noSpace()
            concept.value.cell()
            noSpace()
            "\"".cell()
        }
        conceptEditor(language.NumberLiteral) {
            concept.value.cell {
                validateValue { it.toDoubleOrNull() != null }
            }
        }
        conceptEditor(language.TrueLiteral) {
            "true".cell()
        }
        conceptEditor(language.FalseLiteral) {
            "false".cell()
        }
        conceptEditor(language.InterpolExprWord) {
            brackets(singleLine = true, leftSymbol = "$(", rightSymbol = ")") {
                concept.expr.cell()
            }
        }
        conceptEditor(language.NumberRangeSpec) {
            "[".cell()
            noSpace()
            concept.min.cell {
                validateValue { it.toDoubleOrNull() != null }
                writeReplace { if (it.equals("-inf", ignoreCase = true)) "∞" else it.replace(",", ".") }
            }
            noSpace()
            "|".cell()
            noSpace()
            concept.max.cell {
                writeReplace { if (it.equals("inf", ignoreCase = true)) "∞" else it.replace(",", ".") }
            }
            noSpace()
            "]".cell()
        }
        conceptEditor(language.NumberType) {
            "number".cell()
            optional {
                noSpace()
                concept.range.cell()
            }
            optional {
                noSpace()
                concept.prec.cell()
            }
        }
        conceptEditor(language.StringContainsTarget) {
            "contains".cell()
            noSpace()
            parentheses {
                concept.value.cell()
            }
        }
        conceptEditor(language.StringEndsWithTarget) {
            "endsWith".cell()
            noSpace()
            parentheses {
                concept.value.cell()
            }
        }
        conceptEditor(language.StringInterpolationExpr) {
            brackets(singleLine = true, leftSymbol = "'''", rightSymbol = "'''") {
                concept.text.cell()
            }
        }
        conceptEditor(language.StringLengthTarget) {
            "length".cell()
        }
        conceptEditor(language.StringStartsWithTarget) {
            "startsWith".cell()
            noSpace()
            parentheses {
                concept.value.cell()
            }
        }
        conceptEditor(language.StringToIntTarget) {
            "toInt".cell()
        }

    }

    val base = languageEditors(L_org_iets3_core_expr_base) {
        conceptEditor(language.DotExpression) {
            concept.expr.cell()
            noSpace()
            ".".cell()
            noSpace()
            concept.target.cell()
        }
        conceptEditor(language.IfElseSection) {
            "else".cell()
            concept.expr.cell()
        }
        conceptEditor(language.IfExpression) {
            "if".cell()
            concept.condition.cell()
            "then".cell()
            concept.thenPart.cell()
            optional {
                concept.elseSection.cell()
            }
        }
        conceptEditor(language.IsSomeExpression) {
            "isSome".cell()
            parentheses {
                concept.expr.cell()
            }
            optional {
                "as".cell()
                concept.optionalName.cell()
            }
        }
        conceptEditor(language.NoneLiteral) {
            "none".cell()
            optional {
                noSpace()
                angleBrackets {
                    concept.optionalBaseType.cell()
                }
            }
        }
        conceptEditor(language.ParensExpression) {
            parentheses {
                concept.expr.cell()
            }
        }
        conceptEditor(language.SomeValExpr) {
            concept.someQuery.cell(presentation = {
                expr.get {exprNode ->
                    if (exprNode == null) {
                        null
                    } else {
                        exprNode._node.getReferenceRoles()
                            .map { exprNode._node.getReferenceTarget(it) }
                            .filterIsInstance<N_INamedConcept>()
                            .map { it.name }
                            .firstOrNull()
                    }
                }
            })
        }
        val binaryExpressionSymbols = mapOf<GeneratedConcept<*, *>, String>(
            language.PlusExpression to "+",
            language.MinusExpression to "-",
            language.DivExpression to "/",
            language.MulExpression to "*",
            language.ModExpression to "%",
            language.EqualsExpression to "==",
            language.NotEqualsExpression to "!=",
        )
        conceptEditor(language.BinaryExpression) {
            val symbol = binaryExpressionSymbols[concept._concept]
                ?: "Operator symbol for ${concept._concept.getLongName()} not specified"
            concept.left.cell()
            symbol.cell()
            concept.right.cell()
        }
    }

    val richtext = languageEditors(L_de_slisson_mps_richtext) {
        conceptEditor(language.Text) {
            concept.words.cell()
        }
        conceptEditor(language.Word) {
            concept.escapedValue.cell()
        }
    }

    fun register() {
        tests.register()
        toplevel.register()
        simpleTypes.register()
        base.register()
        richtext.register()
    }
}
