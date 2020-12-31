/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException

class CrateVersion private constructor(val semver: Semver) {
    companion object {
        fun build(text: String): CrateVersion? {
            return try {
                CrateVersion(Semver(text, Semver.SemverType.NPM))
            } catch (e: SemverException) {
                null
            }
        }
    }
}

class CrateVersionRequirement private constructor(private val requirements: List<Requirement>) {
    fun matches(version: CrateVersion): Boolean = requirements.all { it.isSatisfiedBy(version.semver) }

    companion object {
        fun build(text: String): CrateVersionRequirement? {
            val requirements = text.split(",").map { it.trim() }
            val parsed = requirements.map { Requirement.buildNPM(normalizeVersion(it)) }
            if (parsed.size != requirements.size) return null

            return CrateVersionRequirement(parsed)
        }
    }
}

/**
 * Turns 1.2.3 into ^1.2.3.
 */
private fun normalizeVersion(it: String): String {
    return if (it.getOrNull(0)?.isDigit() == true) "^$it"
    else it
}
