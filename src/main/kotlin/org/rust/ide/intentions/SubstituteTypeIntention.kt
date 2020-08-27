/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

class SubstituteTypeIntention : RsElementBaseIntentionAction<SubstituteTypeIntention.Context>() {
    override fun getFamilyName() = "Substitute type"

    data class Context(val path: RsPath,
                       val typeAliasReference: RsTypeReference)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>() ?: return null
        val typeAlias = path.reference?.resolve() as? RsTypeAlias ?: return null
        val type = typeAlias.typeReference ?: return null

        text = when (typeAlias.owner) {
            is RsAbstractableOwner.Impl, is RsAbstractableOwner.Trait -> "Substitute associated type"
            else -> "Substitute type alias"
        }

        return Context(path, type)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val pathTypeRef = ctx.path.parentOfType<RsTypeReference>()

        if (pathTypeRef == null) {
            // expression context, try to extract generic arguments
            val subst = getSubstitution(ctx.typeAliasReference.type, ctx.path)
            ctx.typeAliasReference.type
        } else {
            // type context
            val typeRef = factory.createType(pathTypeRef.type.renderInsertionSafe())
            pathTypeRef.replace(typeRef)
        }

        // S<u32> -> S::<u32> in expression context
        /*val insertedPath: RsPath = if (!isTypeContext && createdPath.typeArgumentList != null) {
            val end = createdPath.identifier?.endOffsetInParent ?: 0
            val pathText = createdPath.text
            val newPath = pathText.substring(0, end) + "::" + pathText.substring(end)
            val path = factory.tryCreatePath(newPath, RustParserUtil.PathParsingMode.TYPE) ?: return
            ctx.path.replace(path) as RsPath
        } else {*/
//            ctx.path.replace(createdPath)
        //}

//        RsImportHelper.importTypeReferencesFromTy(insertedPath, typeRef.type)
    }
}

private fun getSubstitution(ty: Ty, path: RsPath): Substitution {
    val adt = ty as? TyAdt ?: return emptySubstitution

    val parameters = adt.item.typeParameters.mapNotNull { it.typeReference?.type as? TyTypeParameter }
    val arguments = path.typeArguments.map { it.type }
    return Substitution(parameters.zip(arguments).toMap())
}

private fun tryCreatePath(factory: RsPsiFactory, type: Ty): PsiElement? {
    val renderedType = type.renderInsertionSafe()
    return factory.tryCreatePath(renderedType, RustParserUtil.PathParsingMode.TYPE)
        ?: factory.tryCreateTypeQual(renderedType)
}
