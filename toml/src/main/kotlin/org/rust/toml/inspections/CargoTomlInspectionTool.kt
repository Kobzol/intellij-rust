/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.cargo.CargoConstants
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled

abstract class CargoTomlInspectionTool : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        getVisitor(holder) ?: super.buildVisitor(holder, isOnTheFly)

    private fun getVisitor(holder: ProblemsHolder): PsiElementVisitor? {
        if (!isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) return null
        if (holder.file.name != CargoConstants.MANIFEST_FILE) return null

        return buildVisitor(holder)
    }

    abstract fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor?
}
