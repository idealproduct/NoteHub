package com.nqutaoitfteikc.notehub.ui
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.nqutaoitfteikc.notehub.domain.ThemeMode
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme

@Composable
fun NoteHubApp(model: HubViewModel, openedNoteId: String? = null, consumeOpenedNote: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { model.restoreReminders(); model.recoverPhotoFiles() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { model.restoreReminders(); model.recoverPhotoFiles() }
    LaunchedEffect(openedNoteId, state.loading) {
        if (openedNoteId != null && !state.loading) {
            nav.navigate("editor/$openedNoteId/${java.time.LocalDate.now().toEpochDay()}") { launchSingleTop = true }
            consumeOpenedNote()
        }
    }
    val dark = when (state.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    NoteHubTheme(darkTheme = dark) {
        Scaffold(bottomBar = {
            if (entry?.destination?.route?.startsWith("editor") != true && entry?.destination?.route?.startsWith("reminders") != true && entry?.destination?.route?.startsWith("camera") != true && entry?.destination?.route?.startsWith("gallery") != true) NavigationBar {
                listOf("notes" to "記事", "photos" to "照片", "settings" to "設定").forEach { (route, label) ->
                    NavigationBarItem(selected = (entry?.destination?.route ?: "notes") == route,
                        onClick = { nav.navigate(route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        } }, icon = { Text(when (route) { "notes" -> "記"; "photos" -> "照"; else -> "設" }) }, label = { Text(label) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                NavHost(navController = nav, startDestination = "notes", modifier = Modifier.weight(1f)) {
                    composable("notes") {
                        CalendarScreen(state, onAdd = { day -> model.clearError(); nav.navigate("editor/new/$day") },
                            onEdit = { id -> model.clearError(); nav.navigate("editor/$id/${java.time.LocalDate.now().toEpochDay()}") }, onComplete = model::complete,
                            onReminders = { id -> model.clearError(); nav.navigate("reminders/$id") },
                            onPhotos = { id -> model.clearError(); nav.navigate("gallery/$id") })
                    }
                    composable("editor/{id}/{day}") { backStack ->
                        val id = backStack.arguments?.getString("id")?.takeUnless { it == "new" }
                        val day = backStack.arguments?.getString("day")?.toLongOrNull() ?: java.time.LocalDate.now().toEpochDay()
                        val note = state.notes.find { it.id == id }
                        if (state.loading) CircularProgressIndicator()
                        else if (id != null && note == null) {
                            Column(Modifier.padding(20.dp)) {
                                Text("此事項已不存在")
                                Button(onClick = { nav.popBackStack() }) { Text("返回") }
                            }
                        } else NoteEditor(note, day, state.saving, onBack = { nav.popBackStack() },
                            onSave = { draft -> model.save(id, draft) { nav.popBackStack() } },
                            onDelete = { if (id != null) model.delete(id) { nav.popBackStack() } },
                            categories = state.notes.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted())
                    }
                    composable("reminders/{id}") { backStack ->
                        val note = state.notes.find { it.id == backStack.arguments?.getString("id") }
                        if (state.loading) CircularProgressIndicator()
                        else if (note == null) Column(Modifier.padding(16.dp)) {
                            Text("事項已不存在")
                            TextButton(onClick = { nav.popBackStack() }) { Text("返回") }
                        } else RemindersScreen(note, state.reminders.filter { it.noteId == note.id }, model) { nav.popBackStack() }
                    }
                    composable("photos") { PhotoHubScreen(state, model,
                        onCamera = { session, target -> model.clearError(); nav.navigate("camera/$session/${target ?: "new"}") },
                        onNote = { id -> model.clearError(); nav.navigate("gallery/$id") }) }
                    composable("camera/{session}/{target}") { stack ->
                        val session = requireNotNull(stack.arguments?.getString("session"))
                        val targetId = stack.arguments?.getString("target")?.takeUnless { it == "new" }
                        val target = state.notes.find { it.id == targetId }
                        if (state.loading) CircularProgressIndicator()
                        else if (targetId != null && target == null) Column(Modifier.padding(16.dp)) {
                            Text("原記事已不存在，已拍照片仍保留在照片頁的草稿中")
                            TextButton(onClick = { nav.popBackStack() }) { Text("返回") }
                        }
                        else CameraScreen(session, target, state, model, onBack = { nav.popBackStack() }, onSaved = { id ->
                            nav.popBackStack()
                            nav.navigate("gallery/$id") { launchSingleTop = true }
                        })
                    }
                    composable("gallery/{id}") { stack ->
                        val id = stack.arguments?.getString("id")
                        val note = state.notes.find { it.id == id }
                        if (state.loading) CircularProgressIndicator()
                        else if (note?.photoCapturedAt == null) Column(Modifier.padding(16.dp)) {
                            Text("照片記事已不存在")
                            TextButton(onClick = { nav.popBackStack() }) { Text("返回") }
                        } else PhotoGalleryScreen(note, state.photos.filter { it.noteId == id }, state, model,
                            onBack = { nav.popBackStack() },
                            onEdit = { model.clearError(); nav.navigate("editor/${note.id}/0") },
                            onCamera = { session -> model.clearError(); nav.navigate("camera/$session/${note.id}") })
                    }
                    composable("import") {
                        val importModel: CalendarImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = model.calendarImportFactory)
                        CalendarImportScreen(importModel) { nav.popBackStack() }
                    }
                    composable("settings") { SettingsScreen(state.theme, model::setTheme, model) { nav.navigate("import") } }
                }
            }
        }
    }
}
@Composable
private fun SettingsScreen(theme: ThemeMode, setTheme: (ThemeMode) -> Unit, model: HubViewModel, onImport: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("設定", style = MaterialTheme.typography.headlineLarge) }
        item { Text("外觀", style = MaterialTheme.typography.titleLarge) }
        items(ThemeMode.entries) { mode ->
            Row(Modifier.fillMaxWidth().selectable(selected = mode == theme, role = Role.RadioButton, onClick = { setTheme(mode) }).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RadioButton(selected = mode == theme, onClick = null)
                Text(when (mode) { ThemeMode.SYSTEM -> "跟隨系統"; ThemeMode.LIGHT -> "淺色"; ThemeMode.DARK -> "深色" })
            }
        }
        item { ReminderPermissions(model.reminderPlatform, model::restoreReminders) }
        item { Button(onClick = onImport) { Text("Google 行事曆匯入") } }
        item { Text("資料儲存", style = MaterialTheme.typography.titleLarge) }
        item { Text("記事與設定保存在此裝置。解除安裝會刪除資料，目前不提供雲端備份。") }
        item { Text("NoteHub 0.5.0 · 本機記事與 Google 行事曆唯讀匯入") }
    }
}




