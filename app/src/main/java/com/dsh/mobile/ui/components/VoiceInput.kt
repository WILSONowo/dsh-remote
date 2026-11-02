package com.dsh.mobile.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** System dictation only; result is inserted into the draft and is never sent automatically. */
@Composable
fun rememberVoiceInput(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }?.let(latestResult)
        }
    }
    val launch = {
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "对着手机说话")
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "此设备不支持语音输入", Toast.LENGTH_SHORT).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch() else Toast.makeText(context, "需要麦克风权限", Toast.LENGTH_SHORT).show()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launch()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
