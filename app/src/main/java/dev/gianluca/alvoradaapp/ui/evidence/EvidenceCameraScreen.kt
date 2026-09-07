package dev.gianluca.alvoradaapp.ui.evidence

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.data.EvidencePhotoEntity
import dev.gianluca.alvoradaapp.data.MissionRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Captura da evidência. Aceita **várias fotos** por missão — uma dose de remédio pode
 * exigir a cartela e o copo, um treino pode exigir antes e depois.
 *
 * Usada em dois lugares com o mesmo código: dentro da `AlarmActivity`, logo depois de
 * dispensar (e portanto acessível por cima do lock screen), e pela tela de Evidências,
 * quando a foto vem depois.
 *
 * O desenho é de câmera, não de formulário com uma câmera dentro: a imagem ocupa tudo,
 * os controles flutuam sobre degradês em vez de barras opacas, e o obturador é um
 * círculo grande e centralizado. Isso importa mais aqui do que em qualquer outra tela
 * do app — quem chega nela costuma estar recém-acordado, com pressa, segurando o
 * celular com uma mão só.
 */
@Composable
fun EvidenceCameraScreen(
    instanceId: Long,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val repository = container.missionRepository
    val scope = rememberCoroutineScope()

    var run by remember { mutableStateOf<MissionRun?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val photos by repository.observePhotos(instanceId).collectAsState(initial = emptyList())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(instanceId) {
        run = repository.getRun(instanceId)
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            CameraPreview(imageCapture = imageCapture, modifier = Modifier.fillMaxSize())
        } else {
            NoPermission(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        Header(
            title = run?.mission?.title ?: "Evidência",
            subtitle = when (photos.size) {
                0 -> "Nenhuma foto ainda"
                1 -> "1 foto"
                else -> "${photos.size} fotos"
            },
            onCancel = onCancel,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Controls(
            photos = photos,
            capturing = capturing,
            canCapture = hasPermission && run != null,
            error = error,
            onRemove = { photo -> scope.launch { repository.removePhoto(photo) } },
            onCancel = onCancel,
            onShutter = {
                val currentRun = run ?: return@Controls
                capturing = true
                error = null
                val file = repository.newPhotoFile(instanceId, currentRun.instance.date)
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            scope.launch {
                                repository.addPhoto(instanceId, file.absolutePath)
                                capturing = false
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            error = "Não deu para salvar a foto: ${exception.message}"
                            capturing = false
                        }
                    },
                )
            },
            onFinish = {
                scope.launch {
                    if (repository.complete(instanceId)) onFinished()
                    else error = "Esta missão exige pelo menos uma foto."
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ------------------------------------------------------------------------ topo

@Composable
private fun Header(
    title: String,
    subtitle: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Degradê em vez de barra opaca: o preto sólido cortava a imagem em duas e fazia a
    // tela parecer um formulário com uma câmera embutida.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                )
            )
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ---------------------------------------------------------------------- rodapé

@Composable
private fun Controls(
    photos: List<EvidencePhotoEntity>,
    capturing: Boolean,
    canCapture: Boolean,
    error: String?,
    onRemove: (EvidencePhotoEntity) -> Unit,
    onCancel: () -> Unit,
    onShutter: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                )
            )
            .padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = photos.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    Thumbnail(photo = photo, onRemove = { onRemove(photo) })
                }
            }
        }

        if (photos.isEmpty()) {
            Text(
                text = "Fotografe o que comprova a missão. Pode tirar mais de uma.",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Os dois lados têm a mesma largura para o obturador ficar no centro óptico
            // da tela, e não no centro do que sobrou.
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                TextButton(onClick = onCancel) {
                    Text("Depois", color = Color.White.copy(alpha = 0.85f))
                }
            }

            Shutter(enabled = canCapture && !capturing, onClick = onShutter)

            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                FinishButton(visible = photos.isNotEmpty(), onClick = onFinish)
            }
        }
    }
}

/**
 * Só aparece quando há o que concluir.
 *
 * Extraído para fora do `Row` de propósito: ali dentro, `AnimatedVisibility` resolveria
 * para a sobrecarga de `RowScope` mesmo estando dentro de um `Box`.
 */
@Composable
private fun FinishButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Concluir", fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * O obturador clássico: anel externo fixo, disco interno que encolhe ao disparar.
 *
 * A animação é o recibo de que o toque chegou. Sem ela, o intervalo entre apertar e a
 * miniatura aparecer é tempo suficiente para alguém apertar de novo e tirar duas fotos
 * iguais.
 */
@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(targetValue = if (enabled) 1f else 0.82f, label = "shutter")

    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White.copy(alpha = if (enabled) 0.9f else 0.4f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.45f))
        )
    }
}

@Composable
private fun Thumbnail(photo: EvidencePhotoEntity, onRemove: () -> Unit) {
    Box(Modifier.size(72.dp)) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remover foto",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun NoPermission(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Sem acesso à câmera",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A evidência é uma foto — sem câmera não há como comprovar a missão.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Permitir") }
    }
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        // `get()` bloqueia até o provedor iniciar — fora da thread principal.
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
