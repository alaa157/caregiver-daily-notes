package com.caregiver.mobile.data.demo

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 7: the demo runtime path never touches the network. Every file that
 * ships a spec screen, demo ViewModel, repository, stub, rule, session, or
 * the root gate is scanned for network references. Legacy network files
 * (data/api, core/network, AuthRepository, pre-demo ViewModels/screens)
 * stay compiling for the backend flavor but are unreachable from the demo
 * UI: AppGraph builds Retrofit lazily and nothing below references it.
 */
class NoNetworkInDemoTest {

    private fun srcMain(): File {
        val candidates = listOf(
            File("src/main/java/com/caregiver/mobile"),
            File("app/src/main/java/com/caregiver/mobile"),
            File("android/app/src/main/java/com/caregiver/mobile"),
            File(System.getProperty("user.dir") + "/src/main/java/com/caregiver/mobile"),
            File(System.getProperty("user.dir") + "/app/src/main/java/com/caregiver/mobile"),
            File(System.getProperty("user.dir") + "/android/app/src/main/java/com/caregiver/mobile"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("main sources not found from user.dir=" + System.getProperty("user.dir"))
    }

    /** Demo runtime files, relative to .../java/com/caregiver/mobile. */
    private fun demoFiles(root: File): List<File> = (
        root.resolve("data/demo").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
            listOf(
                "CaregiverRoot.kt",
                "MainScaffold.kt",
                "presentation/auth/DemoSignInScreen.kt",
                "presentation/auth/DemoSignInViewModel.kt",
                "presentation/home/HomeScreen.kt",
                "presentation/home/HomeDemoViewModel.kt",
                "presentation/recipients/RecipientScreens.kt",
                "presentation/recipients/RecipientsDemoViewModel.kt",
                "presentation/recipients/RecipientDetailDemoViewModel.kt",
                "presentation/notes/NoteEditorScreen.kt",
                "presentation/notes/EditorDemoViewModel.kt",
                "presentation/notes/NoteDetailScreen.kt",
                "presentation/notes/NoteDetailDemoViewModel.kt",
                "presentation/notes/NoteScreens.kt",
                "presentation/history/HistoryScreen.kt",
                "presentation/history/HistoryDemoViewModel.kt",
                "presentation/summary/SummaryScreen.kt",
                "presentation/summary/SummaryDemoViewModel.kt",
                "presentation/plans/PlanScreens.kt",
                "presentation/plans/PlanDemoViewModels.kt",
                "presentation/saved/SavedScreen.kt",
                "presentation/saved/SavedDemoViewModel.kt",
                "presentation/settings/SettingsScreens.kt",
                "presentation/common/DesignSystem.kt",
            ).map { root.resolve(it) }
        ).filter { it.isFile }

    @Test
    fun demoPathHasNoNetworkReferences() {
        val root = srcMain()
        val markers = listOf(
            "graph.apis", "graph.auth", "Retrofit", "retrofit2", "OkHttp", "okhttp3",
            "AuthRepository", "BackendApis", "TokenHolder",
        )
        val offenders = mutableListOf<String>()
        demoFiles(root).forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                markers.firstOrNull { line.contains(it) }?.let { marker ->
                    offenders.add(
                        "${file.relativeTo(root).path}:${index + 1} references '$marker'",
                    )
                }
            }
        }
        assertTrue(
            "network references in demo runtime path:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
