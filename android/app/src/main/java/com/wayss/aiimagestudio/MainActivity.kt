package com.wayss.aiimagestudio

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WayssTheme {
                WayssApp()
            }
        }
    }
}

@Composable
private fun WayssTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB8A7FF),
            secondary = Color(0xFF6FE7DD),
            background = Color(0xFF08080D),
            surface = Color(0xFF12121A)
        ),
        content = content
    )
}

@Composable
private fun WayssApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var prompt by remember { mutableStateOf("") }
    var resultBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var originalBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val promptChips = listOf(
        "Make it cinematic",
        "Background change",
        "1980s photo",
        "Improve lighting",
        "Remove background"
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        resultBitmap = null
        error = null
        uri?.let {
            originalBitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080D))
    ) {
        AnimatedBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "WAYSS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "AI IMAGE STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column {
                Text(
                    "Create what you imagine.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Upload an image. Describe the change. Let AI do the rest.",
                    color = Color.White.copy(alpha = .65f)
                )
            }

            ImageCard(
                bitmap = resultBitmap ?: originalBitmap,
                onClick = { launcher.launch("image/*") }
            )

            Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedUri == null) "Choose an image" else "Choose another image")
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(promptChips) { chip ->
                    AssistChip(
                        onClick = { prompt = chip },
                        label = { Text(chip) },
                        leadingIcon = { Icon(Icons.Default.Bolt, null) }
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(20.dp),
                label = { Text("Describe your edit") },
                placeholder = {
                    Text("Example: background ko sunset beach bana do")
                }
            )

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val uri = selectedUri
                                ?: throw IllegalStateException("Please choose an image first.")
                            val input = context.contentResolver.openInputStream(uri)
                                ?: throw IllegalStateException("Could not open the selected image.")
                            val file = File(context.cacheDir, "wayss_input.jpg")
                            input?.use { src ->
                                file.outputStream().use { dst -> src.copyTo(dst) }
                            }

                            val imageBody = file.asRequestBody("image/jpeg".toMediaType())
                            val imagePart = MultipartBody.Part.createFormData(
                                "image", file.name, imageBody
                            )
                            val promptBody = prompt.trim()
                                .toRequestBody("text/plain".toMediaType())

                            val response = ApiClient.api.editImage(imagePart, promptBody)

                            if (!response.isSuccessful) {
                                throw IllegalStateException("Server error ${response.code()}")
                            }

                            val data = response.body()
                                ?: throw IllegalStateException("Empty server response")

                            if (!data.success || data.image.isNullOrBlank()) {
                                throw IllegalStateException(data.error ?: "No image returned")
                            }

                            val bytes = Base64.decode(data.image, Base64.DEFAULT)
                            resultBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (t: Throwable) {
                            error = t.message ?: "Something went wrong"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = selectedUri != null && prompt.isNotBlank() && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                AnimatedContent(targetState = loading, label = "button") { busy ->
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Creating your image…")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate magic")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (resultBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { resultBitmap = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }

                    OutlinedButton(
                        onClick = { /* Save/share can be added next */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SaveAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AnimatedBackground() {
    val transition = rememberInfiniteTransition(label = "bg")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(5000),
            RepeatMode.Reverse
        ),
        label = "shift"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A1C4E).copy(alpha = .35f * shift),
                        Color.Transparent
                    ),
                    radius = 900f
                )
            )
    )
}

@Composable
private fun ImageCard(
    bitmap: android.graphics.Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(
                1.dp,
                Color.White.copy(alpha = .10f),
                RoundedCornerShape(28.dp)
            )
            .background(Color.White.copy(alpha = .04f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Image,
                    null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Drop your imagination here",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap to select an image",
                    color = Color.White.copy(alpha = .55f)
                )
            }
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
