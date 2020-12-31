/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.toml.isDependencyKey
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

abstract class CargoCrateDependencyVisitor : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
        val visitor = object : TomlVisitor() {
            override fun visitTable(element: TomlTable) {
                val names = element.header.names

                val dependencyNameIndex = names.indexOfFirst { it.isDependencyKey }

                // [dependencies], [x86.dev-dependencies], etc.
                if (dependencyNameIndex == names.lastIndex) {
                    element.entries.forEach {
                        val name = it.key.name ?: return@forEach
                        val value = it.value ?: return@forEach
                        if (value is TomlLiteral && value.kind is TomlLiteralKind.String) {
                            handleDependency(Dependency(name, it.key, mapOf("version" to value)))
                        } else if (value is TomlInlineTable) {
                            handleDependency(Dependency(name, it.key, collectProperties(value)))
                        }
                    }
                } else if (dependencyNameIndex != -1) {
                    // [dependencies.crate]
                    val crate = names.getOrNull(dependencyNameIndex + 1)
                    if (crate != null) {
                        val crateName = crate.name
                        if (crateName != null) {
                            handleDependency(Dependency(crateName, crate, collectProperties(element)))
                        }
                    }
                }
            }
        }
        element.accept(visitor)
    }

    abstract fun handleDependency(dependency: Dependency)
}

data class Dependency(val name: String, val nameElement: TomlKey, val properties: Map<String, TomlValue>)

private fun collectProperties(owner: TomlKeyValueOwner): Map<String, TomlValue> {
    return owner.entries.mapNotNull {
        val name = it.key.name ?: return@mapNotNull null
        val value = it.value ?: return@mapNotNull null
        name to value
    }.associate { it }
}
