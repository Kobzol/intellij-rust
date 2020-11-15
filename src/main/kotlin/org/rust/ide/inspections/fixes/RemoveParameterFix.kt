/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.deleteWithSurroundingComma
import org.rust.lang.core.psi.ext.hasSelfParameters
import org.rust.lang.core.psi.ext.topLevelPattern


/**
 * Fix that removes a parameter and all its usages at call sites.
 */
class RemoveParameterFix(binding: RsPatBinding, private val bindingName: String) : LocalQuickFixOnPsiElement(binding) {
    override fun getText() = "Remove parameter `${bindingName}`"
    override fun getFamilyName() = "Remove parameter"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val binding = startElement as? RsPatBinding ?: return
        val patIdent = binding.topLevelPattern as? RsPatIdent ?: return
        val parameter = patIdent.parent as? RsValueParameter ?: return
        val function = parameter.parentOfType<RsFunction>() ?: return

        val parameterIndex = function.valueParameterList?.valueParameterList?.indexOf(parameter) ?: -1
        if (parameterIndex == -1) return

        parameter.deleteWithSurroundingComma()
        removeArguments(function, parameterIndex)
    }
}

private fun removeArguments(function: RsFunction, parameterIndex: Int) {
    ReferencesSearch.search(function).forEach {
        val call = it.element.parentOfTypes(RsCallExpr::class, RsDotExpr::class) ?: return@forEach

        val arguments = when (call) {
            is RsCallExpr -> call.valueArgumentList
            is RsDotExpr -> call.methodCall?.valueArgumentList ?: return@forEach
            else -> return@forEach
        }
        val isMethod = function.hasSelfParameters
        val argumentIndex = when {
            isMethod && call is RsCallExpr -> parameterIndex + 1 // UFCS
            else -> parameterIndex
        }
        arguments.exprList.getOrNull(argumentIndex)?.deleteWithSurroundingComma()
    }
}
