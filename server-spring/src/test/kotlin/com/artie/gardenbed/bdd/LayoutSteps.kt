package com.artie.gardenbed.bdd

import com.lemonappdev.konsist.api.Konsist
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps about where the garden's code lives, read from the production sources by Konsist. */
class LayoutSteps {
    private val scope get() = Konsist.scopeFromProduction()

    @Then("the package {string} holds")
    fun packageHolds(pkg: String, table: DataTable) {
        val held = scope.files.filter { it.packagee?.name == pkg }.map { it.name }.toSet()
        val expected = table.asMaps().map { it.getValue("file") }
        assertTrue(held.containsAll(expected), "$pkg holds $held")
    }

    @Then("no garden package is named {string}")
    fun noPackage(pkg: String) {
        val files = scope.files.filter { it.packagee?.name?.startsWith(pkg) == true }.map { it.path }
        assertEquals(emptyList<String>(), files)
    }

    @Then("{string} is declared in the package {string}")
    fun declaredIn(name: String, pkg: String) {
        val where = scope.properties().filter { it.name == name }.map { it.containingFile.packagee?.name }
        assertEquals(listOf(pkg), where)
    }

    @Then("{string} declares exactly")
    fun declaresExactly(name: String, table: DataTable) {
        val declaration = scope.objects().single { it.name == name }
        val declared = (declaration.properties().map { it.name } + declaration.functions().map { it.name }).toSet()
        assertEquals(table.asMaps().map { it.getValue("member") }.toSet(), declared)
    }
}
