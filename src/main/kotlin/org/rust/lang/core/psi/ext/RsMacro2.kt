/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsMacro2
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsMacro2Stub
import javax.swing.Icon

abstract class RsMacro2ImplMixin : RsStubbedNamedElementImpl<RsMacro2Stub>,
                                   RsMacro2 {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMacro2Stub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getIcon(flags: Int): Icon? = RsIcons.MACRO

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
