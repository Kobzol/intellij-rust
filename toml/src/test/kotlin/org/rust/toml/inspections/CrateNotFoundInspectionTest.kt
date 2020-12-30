/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import org.intellij.lang.annotations.Language
import org.rust.cargo.CargoConstants.MANIFEST_FILE
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.openapiext.runWithEnabledFeature
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CargoRegistryCrateVersion
import org.rust.toml.crates.local.withMockedCrates

class CrateNotFoundInspectionTest : RsInspectionsTestBase(CrateNotFoundInspection::class) {
    fun `test missing crate in dependencies`() = doTest(
        """
        [dependencies]
        <warning descr="Crate foo not found">foo</warning> = "1"
    """
    )

    fun `test missing crate in dev-dependencies`() = doTest(
        """
        [dev-dependencies]
        <warning descr="Crate foo not found">foo</warning> = "1"
    """
    )

    fun `test missing crate complex dependencies`() = doTest(
        """
        [x86.dependencies]
        <warning descr="Crate foo not found">foo</warning> = "1"
    """
    )

    fun `test missing crate in specific dependency`() = doTest(
        """
        [dependencies.<warning descr="Crate foo not found">foo</warning>]
    """
    )

    fun `test existing crate in dependencies`() = doTest(
        """
        [dependencies]
        foo = "1"
    """, crate("foo", "1")
    )

    private fun crate(name: String, vararg versions: String): Crate =
        Crate(name, CargoRegistryCrate(versions.toList().map {
            CargoRegistryCrateVersion(it, false, listOf())
        }))

    private data class Crate(val name: String, val crate: CargoRegistryCrate)

    private fun doTest(@Language("TOML") code: String, vararg crates: Crate) {
        val crateMap = crates.toList().associate { it.name to it.crate }
        myFixture.configureByText(MANIFEST_FILE, code)

        runWithEnabledFeature(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crateMap) {
                myFixture.checkHighlighting()
            }
        }
    }
}
