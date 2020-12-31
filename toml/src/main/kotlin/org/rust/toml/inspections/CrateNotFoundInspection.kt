/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.toml.crates.local.getCrateResolver

class CrateNotFoundInspection : CargoTomlInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor =
        object : CargoCrateDependencyVisitor() {
            override fun handleDependency(dependency: Dependency) {
                checkCrate(dependency, holder)
            }
        }

    private fun checkCrate(dependency: Dependency, holder: ProblemsHolder) {
        val resolver = getCrateResolver()
        if (resolver.getCrate(dependency.name) == null) {
            holder.registerProblem(dependency.nameElement, "Crate ${dependency.name} not found")
        }
    }
}
