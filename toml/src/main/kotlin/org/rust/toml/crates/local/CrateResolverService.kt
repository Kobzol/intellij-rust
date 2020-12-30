/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.components.service

interface CrateResolverService {
    fun getCrate(crateName: String): CargoRegistryCrate?
    fun getAllCrateNames(): List<String>
}

data class CargoRegistryCrate(val versions: List<CargoRegistryCrateVersion>)
data class CargoRegistryCrateVersion(val version: String, val isYanked: Boolean, val features: List<String>)

fun getCrateResolver(): CrateResolverService = service()
