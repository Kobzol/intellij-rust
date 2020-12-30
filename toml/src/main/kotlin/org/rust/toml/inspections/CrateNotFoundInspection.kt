/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import org.rust.cargo.CargoConstants
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import org.rust.toml.crates.local.getCrateResolver
import org.rust.toml.isDependencyKey
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class CrateNotFoundInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        buildVisitor(holder) ?: super.buildVisitor(holder, isOnTheFly)

    private fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor? {
        if (!isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) return null
        if (holder.file.name != CargoConstants.MANIFEST_FILE) return null

        return object : TomlVisitor() {
            override fun visitTable(element: TomlTable) {
                val names = element.header.names

                val dependencyNameIndex = names.indexOfFirst { it.isDependencyKey }

                // [dependencies], [x86.dev-dependencies], etc.
                if (dependencyNameIndex == names.lastIndex) {
                    element.entries.forEach {
                        val name = it.key.name ?: return@forEach
                        val value = it.value ?: return@forEach
                        if (value is TomlLiteral && value.kind is TomlLiteralKind.String) {
                            handleDependency(Dependency(name, it.key, mapOf("version" to value)), holder)
                        }
                        else if (value is TomlInlineTable) {
                            handleDependency(Dependency(name, it.key, collectProperties(value)), holder)
                        }
                    }
                }
                else if (dependencyNameIndex != -1) {
                    // [dependencies.crate]
                    val crate = names.getOrNull(dependencyNameIndex + 1)
                    if (crate != null) {
                        val crateName = crate.name
                        if (crateName != null) {
                            handleDependency(Dependency(crateName, crate, collectProperties(element)), holder)
                        }
                    }
                }
            }
        }
    }

    private fun handleDependency(dependency: Dependency, holder: ProblemsHolder) {
        if (getCrateResolver().getCrate(dependency.name) == null) {
            holder.registerProblem(dependency.nameElement, "Crate ${dependency.name} not found")
        }
    }
}

data class Dependency(val name: String, val nameElement: TomlKey, val properties: Map<String, TomlValue>)

private fun collectProperties(owner: TomlKeyValueOwner): Map<String, TomlValue> {
    return owner.entries.mapNotNull {
        val name = it.key.name ?: return@mapNotNull null
        val value = it.value ?: return@mapNotNull null
        name to value
    }.associate { it }
}
