package dev.flaticols.applecontainer.run

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.flaticols.applecontainer.ContainerSettings

/**
 * On a Dockerfile's first FROM, our marker replaces Docker's and presents a single
 * gutter whose menu offers both "Run on 'Apple Container'" and a "Run on Docker"
 * entry that delegates to Docker's own action. See [AppleContainerDockerfileRunLineMarker].
 */
class DockerfileGutterMergeTest : BasePlatformTestCase() {

    override fun tearDown() {
        ContainerSettings.getInstance().dockerfileGutterCombined = true
        super.tearDown()
    }

    fun `test combined gutter offers both run actions`() {
        ContainerSettings.getInstance().dockerfileGutterCombined = true
        val texts = firstFromGutterItems()
        assertTrue("Our run item is missing: $texts", texts.contains("Run on 'Apple Container'"))
        assertTrue("Docker's delegated run item is missing: $texts", texts.contains("Run on Docker"))
    }

    fun `test gutter shows only our action when combined is off`() {
        ContainerSettings.getInstance().dockerfileGutterCombined = false
        val texts = firstFromGutterItems()
        assertEquals("Only our run action expected: $texts", listOf("Run on 'Apple Container'"), texts)
    }

    private fun firstFromGutterItems(): List<String?> {
        myFixture.configureByText(
            "Dockerfile",
            """
            FROM alpine:3.22 AS build
            COPY go.mod ./
            FROM alpine:3.22
            """.trimIndent(),
        )
        val gutters = myFixture.findAllGutters().filter { it.tooltipText == "Run on 'Apple Container'" }
        assertEquals("Exactly one Apple Container gutter on the first FROM", 1, gutters.size)
        return popupItemTexts(gutters.single())
    }

    private fun popupItemTexts(gutter: com.intellij.codeInsight.daemon.GutterMark): List<String?> {
        val method = gutter.javaClass.methods.first { it.name == "getPopupMenuActions" && it.parameterCount == 0 }
        method.isAccessible = true
        val group = method.invoke(gutter) as ActionGroup
        return group.getChildren(null).map { it.templatePresentation.text }
    }
}
