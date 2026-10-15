package com.nqutaoitfteikc.notehub
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import com.nqutaoitfteikc.notehub.reminders.ReminderPlatform
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nqutaoitfteikc.notehub.ui.*
class MainActivity : ComponentActivity() {
    private var openedNoteId by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedNoteId = intent.getStringExtra(ReminderPlatform.EXTRA_NOTE_ID)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedNoteId = intent.getStringExtra(ReminderPlatform.EXTRA_NOTE_ID)
        enableEdgeToEdge()
        setContent {
            val model: HubViewModel = viewModel(factory = HubViewModel.factory((application as NoteHubApplication).container))
            NoteHubApp(model, openedNoteId) { openedNoteId = null; intent.removeExtra(ReminderPlatform.EXTRA_NOTE_ID) }
        }
    }
}

