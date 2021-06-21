/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.jetbrains.annotations.Nullable
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TYPES
import org.rust.lang.core.resolve.createProcessor
import org.rust.lang.core.resolve.processNestedScopesUpwards
import org.rust.lang.core.stubs.RsStubLiteralKind
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.infer.resolve
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.utils.escapeRust
import org.rust.lang.utils.evaluation.evaluate
import org.rust.stdext.exhaustive
import org.rust.stdext.joinToWithBuffer

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTypeReference.getStubOnlyText(
    subst: Substitution = emptySubstitution,
    renderLifetimes: Boolean = true
): String = TypeSubstitutingPsiRenderer(subst).renderTypeReference(this, renderLifetimes)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsValueParameterList.getStubOnlyText(
    subst: Substitution = emptySubstitution,
    renderLifetimes: Boolean = true
): String = TypeSubstitutingPsiRenderer(subst).renderValueParameterList(this, renderLifetimes)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTraitRef.getStubOnlyText(subst: Substitution = emptySubstitution, renderLifetimes: Boolean = true): String =
    buildString { TypeSubstitutingPsiRenderer(subst).appendPath(this, path, renderLifetimes) }

fun RsPsiRenderer.renderTypeReference(ref: RsTypeReference, renderLifetimes: Boolean): String =
    buildString { appendTypeReference(this, ref, renderLifetimes) }

fun RsPsiRenderer.renderValueParameterList(
    list: RsValueParameterList,
    renderLifetimes: Boolean
): String = buildString { appendValueParameterList(this, list, renderLifetimes) }

fun RsPsiRenderer.renderSelfParameter(
    selfParameter: RsSelfParameter,
    renderLifetimes: Boolean
): String = buildString { appendSelfParameter(this, selfParameter, renderLifetimes) }

fun RsPsiRenderer.renderFunctionSignature(
    fn: RsFunction,
    renderLifetimes: Boolean
): String = buildString { appendFunctionSignature(this, fn, renderLifetimes) }

data class PsiRenderingOptions(
    val renderLifetimes: Boolean
)

open class RsPsiRenderer {
    open fun appendFunctionSignature(sb: StringBuilder, fn: RsFunction, renderLifetimes: Boolean) {
        if (fn.isAsync) {
            sb.append("async ")
        }
        if (fn.isConst) {
            sb.append("const ")
        }
        if (fn.isUnsafe) {
            sb.append("unsafe ")
        }
        if (fn.isExtern) {
            sb.append("extern ")
            val abiName = fn.abiName
            if (abiName != null) {
                sb.append(abiName)
                sb.append(" ")
            }
        }
        sb.append("fn ")
        sb.append(fn.escapedName ?: "")
        val typeParameterList = fn.typeParameterList
        if (typeParameterList != null) {
            appendTypeParameterList(sb, typeParameterList, renderLifetimes)
        }
        val valueParameterList = fn.valueParameterList
        if (valueParameterList != null) {
            appendValueParameterList(sb, valueParameterList, renderLifetimes)
        }
        val retType = fn.retType
        if (retType != null) {
            sb.append(" -> ")
            val retTypeReference = retType.typeReference
            if (retTypeReference != null) {
                appendTypeReference(sb, retTypeReference, renderLifetimes)
            }
        }
        val whereClause = fn.whereClause
        if (whereClause != null) {
            sb.append(" where ")
            whereClause.wherePredList.joinToWithBuffer(sb, separator = ", ") {
                appendWherePred(sb, this, renderLifetimes)
            }
        }
    }

    private fun appendWherePred(sb: StringBuilder, pred: RsWherePred, renderLifetimes: Boolean) {
        val lifetime = pred.lifetime
        val type = pred.typeReference
        if (lifetime != null) {
            sb.append(lifetime.name)
            val bounds = pred.lifetimeParamBounds
            if (bounds != null) {
                appendLifetimeBounds(sb, bounds)
            }
        } else if (type != null) {
            val forLifetimes = pred.forLifetimes
            if (renderLifetimes && forLifetimes != null) {
                appendForLifetimes(sb, forLifetimes)
            }
            appendTypeReference(sb, type, renderLifetimes)
            val typeParamBounds = pred.typeParamBounds
            if (typeParamBounds != null) {
                sb.append(": ")
                typeParamBounds.polyboundList.joinToWithBuffer(sb, " + ") { _ ->
                    appendPolybound(sb, this, renderLifetimes)
                }
            }
        }
    }

    open fun appendTypeParameterList(sb: StringBuilder, list: RsTypeParameterList, renderLifetimes: Boolean) {
        sb.append("<")
        list.stubChildrenOfType<RsElement>().joinToWithBuffer(sb, separator = ", ") {
            when (this) {
                is RsLifetimeParameter -> {
                    sb.append(name)
                    val bounds = lifetimeParamBounds
                    if (bounds != null) {
                        appendLifetimeBounds(sb, bounds)
                    }
                }
                is RsTypeParameter -> {
                    sb.append(name)
                    val bounds = typeParamBounds
                    if (bounds != null) {
                        sb.append(": ")
                        bounds.polyboundList.joinToWithBuffer(sb, " + ") { _ ->
                            appendPolybound(sb, this, renderLifetimes)
                        }
                    }
                    val defaultValue = typeReference
                    if (defaultValue != null) {
                        sb.append(" = ")
                        appendTypeReference(sb, defaultValue, renderLifetimes)
                    }
                }
                is RsConstParameter -> {
                    sb.append("const ")
                    sb.append(name ?: "_")
                    val type = typeReference
                    if (type != null) {
                        appendTypeReference(sb, type, renderLifetimes)
                    }
                }
            }
        }
        sb.append(">")
    }

    private fun appendLifetimeBounds(sb: StringBuilder, bounds: @Nullable RsLifetimeParamBounds) {
        sb.append(": ")
        bounds.lifetimeList.joinToWithBuffer(sb, separator = " + ") { it.append(name) }
    }

    open fun appendValueParameterList(
        sb: StringBuilder,
        list: RsValueParameterList,
        renderLifetimes: Boolean
    ) {
        sb.append("(")
        val selfParameter = list.selfParameter
        val valueParameterList = list.valueParameterList
        if (selfParameter != null) {
            appendSelfParameter(sb, selfParameter, renderLifetimes)
            if (valueParameterList.isNotEmpty()) {
                sb.append(", ")
            }
        }
        valueParameterList.joinToWithBuffer(sb, separator = ", ") { sb1 ->
            sb1.append(patText ?: "_")
            sb1.append(": ")
            val typeReference = typeReference
            if (typeReference != null) {
                appendTypeReference(sb1, typeReference, renderLifetimes)
            } else {
                sb1.append("()")
            }
        }
        sb.append(")")
    }

    open fun appendSelfParameter(
        sb: StringBuilder,
        selfParameter: RsSelfParameter,
        renderLifetimes: Boolean
    ) {
        val typeReference = selfParameter.typeReference
        if (typeReference != null) {
            sb.append("self: ")
            appendTypeReference(sb, typeReference, renderLifetimes)
        } else {
            if (selfParameter.isRef) {
                sb.append("&")
                val lifetime = selfParameter.lifetime
                if (renderLifetimes && lifetime != null) {
                    appendLifetime(sb, lifetime)
                    sb.append(" ")
                }
                sb.append(if (selfParameter.mutability.isMut) "mut " else "")
            }
            sb.append("self")
        }
    }

    open fun appendTypeReference(sb: StringBuilder, ref: RsTypeReference, renderLifetimes: Boolean) {
        when (val type = ref.skipParens()) {
            is RsTupleType ->
                type.typeReferenceList.joinToWithBuffer(sb, ", ", "(", ")") {
                    appendTypeReference(it, this, renderLifetimes)
                }

            is RsBaseType -> when (val kind = type.kind) {
                RsBaseTypeKind.Unit -> sb.append("()")
                RsBaseTypeKind.Never -> sb.append("!")
                RsBaseTypeKind.Underscore -> sb.append("_")
                is RsBaseTypeKind.Path -> appendPath(sb, kind.path, renderLifetimes)
            }

            is RsRefLikeType -> {
                if (type.isPointer) {
                    sb.append(if (type.mutability.isMut) "*mut " else "*const ")
                } else if (type.isRef) {
                    sb.append("&")
                    val lifetime = type.lifetime
                    if (renderLifetimes && lifetime != null) {
                        appendLifetime(sb, lifetime)
                        sb.append(" ")
                    }
                    if (type.mutability.isMut) sb.append("mut ")
                }
                type.typeReference?.let { appendTypeReference(sb, it, renderLifetimes) }
            }

            is RsArrayType -> {
                sb.append("[")
                type.typeReference?.let { appendTypeReference(sb, it, renderLifetimes) }
                if (!type.isSlice) {
                    val arraySizeExpr = type.expr
                    sb.append("; ")
                    if (arraySizeExpr != null) {
                        appendConstExpr(sb, arraySizeExpr, TyInteger.USize)
                    } else {
                        sb.append("{}")
                    }
                }
                sb.append("]")
            }

            is RsFnPointerType -> {
                if (type.isUnsafe) {
                    sb.append("unsafe ")
                }
                if (type.isExtern) {
                    sb.append("extern ")
                    val abiName = type.abiName
                    if (abiName != null) {
                        sb.append(abiName)
                        sb.append(" ")
                    }
                }
                sb.append("fn")
                appendValueParameterListTypes(sb, type.valueParameters, renderLifetimes)
                appendRetType(sb, type.retType, renderLifetimes)
            }

            is RsTraitType -> {
                sb.append(if (type.isImpl) "impl " else "dyn ")
                type.polyboundList.joinToWithBuffer(sb, " + ") { _ ->
                    appendPolybound(sb, this, renderLifetimes)
                }
            }

            is RsMacroType -> {
                appendPath(sb, type.macroCall.path, renderLifetimes = false)
                sb.append("!(")
                val macroBody = type.macroCall.macroBody
                if (macroBody != null) {
                    sb.append(macroBody)
                }
                sb.append(")")
            }
        }
    }

    private fun appendPolybound(sb: StringBuilder, polyBound: RsPolybound, renderLifetimes: Boolean) {
        val forLifetimes = polyBound.forLifetimes
        if (renderLifetimes && forLifetimes != null) {
            appendForLifetimes(sb, forLifetimes)
        }
        if (polyBound.hasQ) {
            sb.append("?")
        }

        val bound = polyBound.bound
        val lifetime = bound.lifetime
        if (renderLifetimes && lifetime != null) {
            sb.append(lifetime.referenceName)
        } else {
            bound.traitRef?.path?.let { appendPath(sb, it, renderLifetimes) }
        }
    }

    private fun appendForLifetimes(sb: StringBuilder, forLifetimes: @Nullable RsForLifetimes) {
        sb.append("for<")
        forLifetimes.lifetimeParameterList.joinTo(sb, ", ") {
            it.name ?: "'_"
        }
        sb.append("> ")
    }

    open fun appendLifetime(sb: StringBuilder, lifetime: RsLifetime) {
        sb.append(lifetime.referenceName)
    }

    open fun appendPath(
        sb: StringBuilder,
        path: RsPath,
        renderLifetimes: Boolean
    ) {
        appendPathWithoutArgs(sb, path, renderLifetimes)
        appendPathArgs(sb, path, renderLifetimes)
    }

    protected open fun appendPathWithoutArgs(sb: StringBuilder, path: RsPath, renderLifetimes: Boolean) {
        path.path?.let { appendPath(sb, it, renderLifetimes) }
        if (path.hasColonColon) {
            sb.append("::")
        }
        sb.append(path.referenceName.orEmpty())
    }

    private fun appendPathArgs(sb: StringBuilder, path: RsPath, renderLifetimes: Boolean) {
        val inAngles = path.typeArgumentList // Foo<...>
        val fnSugar = path.valueParameterList // &dyn FnOnce(...) -> i32
        if (inAngles != null) {
            val lifetimeArguments = inAngles.lifetimeList
            val typeArguments = inAngles.typeReferenceList
            val constArguments = inAngles.exprList
            val assocTypeBindings = inAngles.assocTypeBindingList

            val hasLifetimes = renderLifetimes && lifetimeArguments.isNotEmpty()
            val hasTypeReferences = typeArguments.isNotEmpty()
            val hasConstArguments = constArguments.isNotEmpty()
            val hasAssocTypeBindings = assocTypeBindings.isNotEmpty()

            if (hasLifetimes || hasTypeReferences || hasConstArguments || hasAssocTypeBindings) {
                sb.append("<")
                if (hasLifetimes) {
                    lifetimeArguments.joinToWithBuffer(sb, ", ") { appendLifetime(it, this) }
                    if (hasTypeReferences || hasConstArguments || hasAssocTypeBindings) {
                        sb.append(", ")
                    }
                }
                if (hasTypeReferences) {
                    typeArguments.joinToWithBuffer(sb, ", ") { appendTypeReference(it, this, renderLifetimes) }
                    if (hasConstArguments || hasAssocTypeBindings) {
                        sb.append(", ")
                    }
                }
                if (hasConstArguments) {
                    constArguments.joinToWithBuffer(sb, ", ") { appendConstExpr(it, this) }
                    if (hasAssocTypeBindings) {
                        sb.append(", ")
                    }
                }
                assocTypeBindings.joinToWithBuffer(sb, ", ") { sb ->
                    sb.append(referenceName)
                    sb.append("=")
                    typeReference?.let { appendTypeReference(sb, it, renderLifetimes) }
                }
                sb.append(">")
            }
        } else if (fnSugar != null) {
            appendValueParameterListTypes(sb, fnSugar.valueParameterList, renderLifetimes)
            appendRetType(sb, path.retType, renderLifetimes)
        }
    }

    protected open fun appendRetType(sb: StringBuilder, retType: RsRetType?, renderLifetimes: Boolean) {
        val retTypeRef = retType?.typeReference
        if (retTypeRef != null) {
            sb.append(" -> ")
            appendTypeReference(sb, retTypeRef, renderLifetimes)
        }
    }

    protected open fun appendValueParameterListTypes(
        sb: StringBuilder,
        list: List<RsValueParameter>,
        renderLifetimes: Boolean
    ) {
        list.joinToWithBuffer(sb, separator = ", ", prefix = "(", postfix = ")") { sb ->
            typeReference?.let { appendTypeReference(sb, it, renderLifetimes) }
        }
    }

    protected open fun appendConstExpr(
        sb: StringBuilder,
        expr: RsExpr,
        expectedTy: Ty = expr.type
    ) {
        when (expr) {
            is RsPathExpr -> appendPath(sb, expr.path, false)
            is RsLitExpr -> appendLitExpr(sb, expr)
            is RsBlockExpr -> appendBlockExpr(sb, expr)
            is RsUnaryExpr -> appendUnaryExpr(sb, expr)
            is RsBinaryExpr -> appendBinaryExpr(sb, expr)
            else -> sb.append("{}")
        }
    }

    protected open fun appendLitExpr(sb: StringBuilder, expr: RsLitExpr) {
        when (val kind = expr.stubKind) {
            is RsStubLiteralKind.Boolean -> sb.append(kind.value.toString())
            is RsStubLiteralKind.Integer -> sb.append(kind.value?.toString() ?: "")
            is RsStubLiteralKind.Float -> sb.append(kind.value?.toString() ?: "")
            is RsStubLiteralKind.Char -> {
                if (kind.isByte) {
                    sb.append("b")
                }
                sb.append("'")
                sb.append(kind.value.orEmpty().escapeRust())
                sb.append("'")
            }
            is RsStubLiteralKind.String -> {
                if (kind.isByte) {
                    sb.append("b")
                }
                sb.append('"')
                sb.append(kind.value.orEmpty().escapeRust())
                sb.append('"')
            }
            null -> "{}"
        }.exhaustive
    }

    protected open fun appendBlockExpr(sb: StringBuilder, expr: RsBlockExpr) {
        val isTry = expr.isTry
        val isUnsafe = expr.isUnsafe
        val isAsync = expr.isAsync
        val tailExpr = expr.block.expr

        if (isTry) {
            sb.append("try ")
        }
        if (isUnsafe) {
            sb.append("unsafe ")
        }
        if (isAsync) {
            sb.append("async ")
        }

        if (tailExpr == null) {
            sb.append("{}")
        } else {
            sb.append("{ ")
            appendConstExpr(sb, tailExpr)
            sb.append(" }")
        }
    }

    protected open fun appendUnaryExpr(sb: StringBuilder, expr: RsUnaryExpr) {
        val sign = when (expr.operatorType) {
            UnaryOperator.REF -> "&"
            UnaryOperator.REF_MUT -> "&mut "
            UnaryOperator.DEREF -> "*"
            UnaryOperator.MINUS -> "-"
            UnaryOperator.NOT -> "!"
            UnaryOperator.BOX -> "box "
        }
        sb.append(sign)
        val innerExpr = expr.expr
        if (innerExpr != null) {
            appendConstExpr(sb, innerExpr)
        }
    }

    protected open fun appendBinaryExpr(sb: StringBuilder, expr: RsBinaryExpr) {
        val sign = when (val op = expr.operatorType) {
            is ArithmeticOp -> op.sign
            is ArithmeticAssignmentOp -> op.sign
            AssignmentOp.EQ -> "="
            is ComparisonOp -> op.sign
            is EqualityOp -> op.sign
            LogicOp.AND -> "&&"
            LogicOp.OR -> "||"
        }
        appendConstExpr(sb, expr.left)
        sb.append(" ")
        sb.append(sign)
        sb.append(" ")
        val right = expr.right
        if (right != null) {
            appendConstExpr(sb, right)
        }
    }
}

open class TypeSubstitutingPsiRenderer(private val subst: Substitution) : RsPsiRenderer() {
    override fun appendTypeReference(sb: StringBuilder, ref: RsTypeReference, renderLifetimes: Boolean) {
        val ty = ref.type
        if (ty is TyTypeParameter && subst[ty] != null) {
            sb.append(ty.substAndGetText(subst))
        } else {
            super.appendTypeReference(sb, ref, renderLifetimes)
        }
    }

    override fun appendLifetime(sb: StringBuilder, lifetime: RsLifetime) {
        val resolvedLifetime = lifetime.resolve()
        val substitutedLifetime = if (resolvedLifetime is ReEarlyBound) subst[resolvedLifetime] else null
        if (substitutedLifetime is ReEarlyBound) {
            sb.append(substitutedLifetime.parameter.name)
        } else {
            sb.append(lifetime.referenceName)
        }
    }

    override fun appendConstExpr(
        sb: StringBuilder,
        expr: RsExpr,
        expectedTy: Ty
    ) {
        when (val const = expr.evaluate(expectedTy).substitute(subst)) { // may trigger resolve
            is CtValue -> sb.append(const)
            is CtConstParameter -> {
                val wrapParameterInBraces = expr.stubParent is RsTypeArgumentList

                if (wrapParameterInBraces) {
                    sb.append("{ ")
                }
                sb.append(const.toString())
                if (wrapParameterInBraces) {
                    sb.append(" }")
                }
            }
            else -> sb.append("{}")
        }
    }
}

open class PsiSubstitutingPsiRenderer(private val subst: RsPsiSubstitution) : RsPsiRenderer() {
    override fun appendPathWithoutArgs(sb: StringBuilder, path: RsPath, renderLifetimes: Boolean) {
        val replaced = when (val resolved = path.reference?.resolve()) {
            is RsTypeParameter -> when (val s = subst.typeSubst[resolved]) {
                is RsPsiSubstitution.TypeValue.Present.InAngles -> {
                    super.appendTypeReference(sb, s.value, renderLifetimes)
                    true
                }
                is RsPsiSubstitution.TypeValue.DefaultValue -> {
                    super.appendTypeReference(sb, s.value, renderLifetimes)
                    true
                }
                else -> false
            }
            is RsConstParameter -> when (val s = subst.constSubst[resolved]) {
                is RsPsiSubstitution.Value.Present -> {
                    appendConstExpr(sb, s.value)
                    true
                }
                else -> false
            }
            else -> false
        }
        if (!replaced) {
            super.appendPathWithoutArgs(sb, path, renderLifetimes)
        }
    }

    override fun appendLifetime(sb: StringBuilder, lifetime: RsLifetime) {
        val resolvedLifetime = lifetime.reference.resolve()
        val substitutedLifetime = if (resolvedLifetime is RsLifetimeParameter) {
            subst.regionSubst[resolvedLifetime]
        } else {
            null
        }
        when (substitutedLifetime) {
            is RsPsiSubstitution.Value.Present -> sb.append(substitutedLifetime.value.name)
            else -> sb.append(lifetime.referenceName)
        }
    }
}

class ImportingPsiRenderer(
    subst: RsPsiSubstitution,
    private val context: RsElement
) : PsiSubstitutingPsiRenderer(subst) {

    private val importContext = ImportContext.from(context.project, context)

    private val visibleNames: Pair<MutableMap<String, RsElement>, MutableMap<RsElement, String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val nameToElement = mutableMapOf<String, RsElement>()
        val elementToName = mutableMapOf<RsElement, String>()
        processNestedScopesUpwards(context, TYPES, createProcessor {
            val element = it.element ?: return@createProcessor false
            nameToElement[it.name] = element
            if (it.name != "_" && element !in elementToName) {
                elementToName[element] = it.name
            }
            false
        })
        nameToElement to elementToName
    }
    private val visibleNameToElement: MutableMap<String, RsElement> get() = visibleNames.first
    private val visibleElementToName: MutableMap<RsElement, String> get() = visibleNames.second

    private val itemsToImportMut: MutableSet<ImportCandidate> = mutableSetOf()
    val itemsToImport: Set<ImportCandidate> get() = itemsToImportMut

    override fun appendPathWithoutArgs(sb: StringBuilder, path: RsPath, renderLifetimes: Boolean) {
        if (path.parent !is RsPath && TyPrimitive.fromPath(path) == null && path.basePath().referenceName != "Self") {
            val resolved = path.reference?.resolve()
            val tryReplacePath = resolved !is RsTypeParameter
                && resolved !is RsConstParameter
                && resolved !is RsMacroDefinitionBase
                && resolved !is RsMod
            if (resolved is RsQualifiedNamedElement && tryReplacePath) {
                val visibleElementName = visibleElementToName[resolved]
                if (visibleElementName != null) {
                    sb.append(visibleElementName)
                } else {
                    val importCandidate = ImportCandidatesCollector.getImportCandidates(importContext, resolved).firstOrNull()
                    if (importCandidate == null) {
                        val resolvedCrate = resolved.containingCrate
                        if (resolvedCrate == null || resolvedCrate == context.containingCrate) {
                            sb.append("crate")
                        } else {
                            sb.append(resolvedCrate.normName)
                        }
                        sb.append(resolved.crateRelativePath)
                    } else {
                        val pathReferenceName = path.referenceName.orEmpty()
                        val elementInScopeWithSameName = visibleNameToElement[pathReferenceName]
                        val isNameConflict = elementInScopeWithSameName != null && elementInScopeWithSameName != resolved
                        if (isNameConflict) {
                            val qualifiedPath = importCandidate.info.usePath
                            sb.append(trySimplifyPath(path, qualifiedPath) ?: qualifiedPath)
                        } else {
                            itemsToImportMut += importCandidate
                            visibleNameToElement[pathReferenceName] = resolved
                            visibleElementToName[resolved] = pathReferenceName
                            sb.append(pathReferenceName)
                        }
                    }
                }
                return
            }
        }

        super.appendPathWithoutArgs(sb, path, renderLifetimes)
    }

    private fun trySimplifyPath(originPath: RsPath, qualifiedPath: String): String? {
        val newPath = RsCodeFragmentFactory(originPath.project).createPath(
            qualifiedPath,
            context,
            RustParserUtil.PathParsingMode.TYPE,
            originPath.allowedNamespaces()
        ) ?: return null

        val segmentsReversed = generateSequence(newPath) { it.path }.toList()

        var simplifiedSegmentCount = 1
        var firstSegmentName: String? = null

        for (s in segmentsReversed.asSequence().drop(1)) {
            val resolved = s.reference?.resolve() ?: return null
            simplifiedSegmentCount++
            firstSegmentName = visibleElementToName[resolved]
            if (firstSegmentName != null) break
        }

        return if (firstSegmentName == null || simplifiedSegmentCount >= segmentsReversed.size) {
            null
        } else {
            "$firstSegmentName::" + segmentsReversed
                .take(simplifiedSegmentCount - 1)
                .asReversed()
                .joinToString(separator = "::") { it.referenceName.orEmpty() }
        }
    }
}
