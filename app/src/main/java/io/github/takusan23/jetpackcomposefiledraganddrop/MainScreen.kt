package io.github.takusan23.jetpackcomposefiledraganddrop

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "ドラッグアンドドロップ") })
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {

            DragAndDropSendContainer(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )

            DragAndDropReceiveContainer(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )

            HorizontalDivider()

            ImageDragAndDropSendContainer(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )

            ImageDragAndDropReceiveContainer(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageDragAndDropSendContainer(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 自分のアプリのアイコン
    val bitmap = remember { mutableStateOf<Bitmap?>(null) }
    // 共有で使える Uri
    val shareUri = remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(key1 = Unit) {
        // Bitmap を取り出す
        val iconDrawable = context.packageManager.getApplicationIcon(context.packageName)
        bitmap.value = iconDrawable.toBitmap()

        // ファイル getExternalFilesDir の中に作って保存する
        // images フォルダの images は、 file_paths.xml の path="" が images だからです。
        val imageFolder = context.getExternalFilesDir(null)!!.resolve("images").apply { mkdir() }
        val imageFile = imageFolder.resolve("${System.currentTimeMillis()}.png").apply { createNewFile() }
        imageFile.outputStream().use { outputStream ->
            bitmap.value!!.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        // FileProvider に登録して Uri を取得
        shareUri.value = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
        // 生成される Uri はこんな感じ
        // content://io.github.takusan23.jetpackcomposefiledraganddrop.provider/images/1716833782696.png
    }

    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {

            Text(text = "画像ドラッグアンドドロップ 送信側")

            // 画像を表示、ドラッグアンドドロップも兼ねて
            if (bitmap.value != null) {
                Image(
                    modifier = Modifier.dragAndDropSource {
                        detectTapGestures(onLongPress = {
                            // ClipData へ Uri を
                            val nonnullUri = shareUri.value ?: return@detectTapGestures
                            val clipData = ClipData.newUri(context.contentResolver, "image_uri", nonnullUri)
                            startTransfer(
                                DragAndDropTransferData(
                                    clipData = clipData,
                                    flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ // Uri 読み取り可能ですよのフラグを and で立てておく。ビット演算
                                )
                            )
                        })
                    },
                    bitmap = bitmap.value!!.asImageBitmap(),
                    contentDescription = null
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageDragAndDropReceiveContainer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 受け取った画像
    val receiveBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val isProgressDragAndDrop = remember { mutableStateOf(false) }
    val callback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val androidEvent = event.toAndroidDragEvent()

                // DragAndDropPermission を作らないと、Uri を使ったアクセスが出来ません
                val dragAndDropPermissions = ActivityCompat.requestDragAndDropPermissions(context as Activity, androidEvent)
                // 最初のデータを取り出します
                val receiveUri = androidEvent.clipData.getItemAt(0).uri

                // UI 処理なので一応コルーチンで
                scope.launch(Dispatchers.IO) {
                    // Uri から Bitmap を作る
                    // Glide や Coil が使えるなら使うべきです
                    receiveBitmap.value = context.contentResolver.openInputStream(receiveUri)
                        ?.use { inputStream -> BitmapFactory.decodeStream(inputStream) }
                    // とじる
                    dragAndDropPermissions?.release()
                }
                return true
            }

            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)
                isProgressDragAndDrop.value = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)
                isProgressDragAndDrop.value = false
            }
        }
    }

    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {

            Text(text = "画像ドラッグアンドドロップ 受信側")

            // ドラッグアンドドロップを待ち受ける
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary)
                    .background(
                        // ドラッグアンドドロップ操作中はコンテナの背景色を変化
                        color = if (isProgressDragAndDrop.value) {
                            MaterialTheme.colorScheme.primary.copy(0.5f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            // 受け取れる種類。とりあえず
                            val supportedMimeTypePrefix = "image/"
                            event
                                .mimeTypes()
                                .all { receiveMimeType -> receiveMimeType.startsWith(supportedMimeTypePrefix) }
                        },
                        target = callback
                    )
            ) {
                // 画像表示
                if (receiveBitmap.value != null) {
                    Image(
                        bitmap = receiveBitmap.value!!.asImageBitmap(),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DragAndDropSendContainer(modifier: Modifier = Modifier) {
    val inputText = remember { mutableStateOf("") }

    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {

            Text(text = "ドラッグアンドドロップ 送信側")

            OutlinedTextField(
                value = inputText.value,
                onValueChange = { inputText.value = it }
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary)
                    .dragAndDropSource {
                        detectTapGestures(onLongPress = {
                            // value に文字をいれる
                            val clipData = ClipData.newPlainText("Text", inputText.value)
                            startTransfer(DragAndDropTransferData(clipData = clipData, flags = View.DRAG_FLAG_GLOBAL))
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "長押し！")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DragAndDropReceiveContainer(modifier: Modifier = Modifier) {
    val receiveText = remember { mutableStateOf("") }
    val isProgressDragAndDrop = remember { mutableStateOf(false) }
    val callback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val androidEvent = event.toAndroidDragEvent()
                // 最初のデータを取り出します
                val mimeType = androidEvent.clipDescription.getMimeType(0)
                val text = androidEvent.clipData.getItemAt(0).text
                receiveText.value = """
                    受信したデータ
                    MIME-Type: $mimeType
                    text: $text
                """.trimIndent()
                return true
            }

            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)
                isProgressDragAndDrop.value = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)
                isProgressDragAndDrop.value = false
            }
        }
    }

    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {

            Text(text = "ドラッグアンドドロップ 受信側")

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary)
                    .background(
                        // ドラッグアンドドロップ操作中はコンテナの背景色を変化
                        color = if (isProgressDragAndDrop.value) {
                            MaterialTheme.colorScheme.primary.copy(0.5f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            // 受け取れる種類。とりあえずテキスト
                            event
                                .mimeTypes()
                                .contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        },
                        target = callback
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "ここに持ってくる")
            }

            HorizontalDivider()

            Text(text = receiveText.value)
        }
    }
}