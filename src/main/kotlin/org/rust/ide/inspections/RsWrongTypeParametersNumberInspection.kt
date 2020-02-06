/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.ide.inspections.fixes.AddTypeParameters
import org.rust.ide.inspections.fixes.RemoveTypeParameter
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration

/**
 * Inspection that detects E0243/E0244/E0087/E0089/E0035/E0036 errors.
 */
class RsWrongTypeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return
                checkMethod(holder, type)
            }

            override fun visitCallExpr(o: RsCallExpr) = checkMethod(holder, o)
            override fun visitMethodCall(o: RsMethodCall) = checkMethod(holder, o)
        }

    private fun checkMethod(holder: RsProblemsHolder, o: RsElement) {
        val (actualArguments, declaration) = when (o) {
            is RsMethodCall ->
                o.typeArgumentList to o.reference.resolve()

            is RsCallExpr ->
                (o.expr as? RsPathExpr)?.path?.typeArgumentList to (o.expr as? RsPathExpr)?.path?.reference?.resolve()

            is RsBaseType ->
                o.path?.typeArgumentList to o.path?.reference?.resolve()

            else -> return
        }
        if (declaration !is RsGenericDeclaration) return
        val nArguments = actualArguments?.typeReferenceList?.size ?: 0

        val expectedRequiredParams = declaration.typeParameterList?.typeParameterList?.filter { it.typeReference == null }?.size ?: 0
        val expectedTotalParams = declaration.typeParameterList?.typeParameterList?.size ?: 0

        val data = when(o) {
            is RsBaseType -> checkBaseType(nArguments, expectedRequiredParams, expectedTotalParams)
            is RsMethodCall -> checkMethodCall(nArguments, expectedRequiredParams, expectedTotalParams)
            is RsCallExpr -> checkCallExpr(nArguments, expectedRequiredParams, expectedTotalParams)
            else -> null
        } ?: return

        val problemText = "Wrong number of type parameters: expected ${data.expectedText}, found $nArguments [${data.code}]"
        holder.registerProblem(o, problemText, data.fix)
    }

    data class ProblemData(val expectedText: String, val code: String, val fix: LocalQuickFix? = null)

    private fun getFix(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): LocalQuickFix? {
        return when {
            expectedTotalParams == 0 -> {
                RemoveTypeParameter()
            }
            actualArgs < expectedRequiredParams -> {
                AddTypeParameters()
            }
            else -> {
                null
            }
        }
    }

    private fun checkBaseType(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs < expectedRequiredParams ->
                ("E0243" to if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams ->
                ("E0244" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, getFix(actualArgs, expectedRequiredParams, expectedTotalParams))
    }

    private fun checkMethodCall(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs != 0 && expectedTotalParams == 0 ->
                ("E0035" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedRequiredParams" else "$expectedTotalParams")
            actualArgs > expectedTotalParams ->
                ("E0036" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, getFix(actualArgs, expectedRequiredParams, expectedTotalParams))
    }

    private fun checkCallExpr(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): ProblemData? {
        val (code, expectedText) = when {
            actualArgs > expectedTotalParams ->
                ("E0087" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
            else -> null
        } ?: return null
        return ProblemData(expectedText, code, getFix(actualArgs, expectedRequiredParams, expectedTotalParams))
    }
}
