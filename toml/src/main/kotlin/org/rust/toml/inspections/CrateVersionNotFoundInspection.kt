/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.toml.CrateVersion
import org.rust.toml.CrateVersionRequirement
import org.rust.toml.crates.local.getCrateResolver
import org.rust.toml.stringValue

class CrateVersionNotFoundInspection : CargoTomlInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor =
        object : CargoCrateDependencyVisitor() {
            override fun handleDependency(dependency: Dependency) {
                checkCrate(dependency, holder)
            }
        }

    private fun checkCrate(dependency: Dependency, holder: ProblemsHolder) {
        val crate = getCrateResolver().getCrate(dependency.name) ?: return
        val version = dependency.properties["version"] ?: return
        val versionText = version.stringValue ?: return
        val versionReq = CrateVersionRequirement.build(versionText) ?: return
        if (!crate.versions.mapNotNull { CrateVersion.build(it.version) }.any {
                versionReq.matches(it)
            }) {
            holder.registerProblem(version, "No version found matching $versionText")
        }
    }
}
