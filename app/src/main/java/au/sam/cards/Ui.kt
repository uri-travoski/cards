package au.sam.cards

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Corner radii: Material's defaults and the earlier look, each made 20% tighter.
private val SmallShape = RoundedCornerShape(6.4.dp)
private val CardShape = RoundedCornerShape(14.4.dp)
val RowShape = RoundedCornerShape(11.2.dp)
private val PhotoShape = RoundedCornerShape(9.6.dp)
val ButtonShape = RoundedCornerShape(percent = 40) // pill buttons are 50%

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.2.dp), small = SmallShape, medium = PhotoShape,
    large = RoundedCornerShape(12.8.dp), extraLarge = RoundedCornerShape(22.4.dp),
)

@Composable
private fun Btn(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier, enabled, shape = ButtonShape, content = content)

@Composable
private fun OBtn(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    OutlinedButton(onClick, modifier, enabled, shape = ButtonShape, content = content)

@Composable
private fun TBtn(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    TextButton(onClick, modifier, enabled, shape = ButtonShape, content = content)

private sealed interface Screen {
    object Home : Screen
    class Detail(val id: String) : Screen
    class Edit(val card: Card) : Screen
}

// Warm orange palette. Primary is a deep orange so white text on buttons stays readable (contrast above 4.5).
private val OrangeLight = lightColorScheme(
    primary = Color(0xFFC25E00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2), onPrimaryContainer = Color(0xFF3B1A00),
    secondary = Color(0xFF8A5100), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B8), onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF6C5E00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E58A), onTertiaryContainer = Color(0xFF211B00),
    background = Color(0xFFFFF8F3), onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8F3), onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFF5DFD0), onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF857468), outlineVariant = Color(0xFFD8C3B5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1E6), surfaceContainer = Color(0xFFFBEADB),
    surfaceContainerHigh = Color(0xFFF6E4D4), surfaceContainerHighest = Color(0xFFF0DECE),
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB77C), onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFF6C3A00), onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFFFB95C), onSecondary = Color(0xFF472A00),
    secondaryContainer = Color(0xFF653E00), onSecondaryContainer = Color(0xFFFFDDB4),
    tertiary = Color(0xFFDAC96F), onTertiary = Color(0xFF393000),
    tertiaryContainer = Color(0xFF524600), onTertiaryContainer = Color(0xFFF7E58A),
    background = Color(0xFF1E1610), onBackground = Color(0xFFEDE0D5),
    surface = Color(0xFF1E1610), onSurface = Color(0xFFEDE0D5),
    surfaceVariant = Color(0xFF52443A), onSurfaceVariant = Color(0xFFD8C3B5),
    outline = Color(0xFFA08D80), outlineVariant = Color(0xFF52443A),
    surfaceContainerLowest = Color(0xFF181009), surfaceContainerLow = Color(0xFF271E17), surfaceContainer = Color(0xFF2B221A),
    surfaceContainerHigh = Color(0xFF362C24), surfaceContainerHighest = Color(0xFF41372E),
)

@Composable
fun App(a: MainActivity) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) OrangeDark else OrangeLight,
        shapes = AppShapes,
    ) {
        val view = LocalView.current
        val dark = isSystemInDarkTheme()
        SideEffect {
            // The status bar strip below is the primary colour: dark in light theme (light icons), light in dark theme (dark icons).
            (view.context as? android.app.Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = dark }
        }
        Surface(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.systemBarsPadding().imePadding()) {
                when {
                    !a.unlocked -> LockScreen(a)
                    a.store.storage.isEmpty() || a.changingStorage -> SetupScreen(a)
                    else -> Cards(a)
                }
            }
        }
          }
    }
}

@Composable
private fun LockScreen(a: MainActivity) = Column(
    Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally,
) {
    Text("Cards are locked", fontSize = 22.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(24.dp))
    Btn({ a.requireUnlock() }) { Text("Unlock") }
}

@Composable
private fun SetupScreen(a: MainActivity) {
    var s3Form by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    if (help) HelpDialog { help = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (a.store.storage.isNotEmpty()) TBtn({ if (s3Form) s3Form = false else a.changingStorage = false }) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
            IconButton({ help = true }) { Icon(HelpIcon, "Help") }
        }
        Text("Cards (Open Source)", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (!s3Form) {
            Text("Where should your cards be saved? They are kept in the cloud, so any phone you connect gets them all back.")
            Choice("Google Drive", "Sign in with your Google account. Stored in a hidden app folder of your Drive. Needs a one-time Google Cloud setup: tap the help icon for the steps.") { a.signIn() }
            Choice("Cloud folder", "Pick a folder inside Dropbox, OneDrive, Nextcloud or another cloud app on this phone.") { a.pickFolder() }
            Choice("S3 / Backblaze B2 / Cloudflare R2", "Your own bucket on any S3-compatible service.") { s3Form = true }
        } else S3Form(a)
        if (a.status.isNotEmpty()) Text(a.status, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

@Composable
private fun Choice(title: String, text: String, onClick: () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(CardShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).clickable(onClick = onClick).padding(16.dp),
) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun S3Form(a: MainActivity) {
    var cfg by remember { mutableStateOf(a.store.s3) }
    Text("Create a bucket and an access key that can read and write it. Everything goes under a cards/ prefix.", fontSize = 13.sp)
    Txt("Endpoint", cfg.endpoint, placeholder = "s3.us-west-004.backblazeb2.com") { cfg = cfg.copy(endpoint = it.trim()) }
    Text(
        "Backblaze B2: s3.<region>.backblazeb2.com  ·  Cloudflare R2: <account id>.r2.cloudflarestorage.com (region auto)  ·  AWS: s3.<region>.amazonaws.com",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
    )
    Txt("Region", cfg.region, placeholder = "auto, us-west-004, us-east-1…") { cfg = cfg.copy(region = it.trim()) }
    Txt("Bucket", cfg.bucket) { cfg = cfg.copy(bucket = it.trim()) }
    Txt("Access key ID", cfg.accessKey) { cfg = cfg.copy(accessKey = it.trim()) }
    Txt("Secret access key", cfg.secretKey, secret = true) { cfg = cfg.copy(secretKey = it.trim()) }
    Btn({ a.connectS3(cfg) }, Modifier.fillMaxWidth(), enabled = cfg.complete) { Text("Test and save") }
}

@Composable
private fun Cards(a: MainActivity) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var settings by remember { mutableStateOf(false) }
    when (val s = screen) {
        Screen.Home -> Home(a, onOpen = { screen = Screen.Detail(it) }, onAdd = { screen = Screen.Edit(Card()) }, onSettings = { settings = true })
        is Screen.Detail -> {
            val card = a.store.get(s.id)
            if (card == null) screen = Screen.Home
            else Detail(a, card, onBack = { screen = Screen.Home }, onEdit = { screen = Screen.Edit(card) })
        }
        is Screen.Edit -> Edit(a, s.card, onDone = { id -> screen = if (id == null || a.store.get(id) == null) Screen.Home else Screen.Detail(id) })
    }
    if (settings) SettingsDialog(a) { settings = false }
}

@Composable
private fun SectionHeader(text: String, count: Int) = Text(
    "$text · $count", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp),
)

@Composable
private fun Home(a: MainActivity, onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val shown = a.store.cards.filter {
        (type.isEmpty() || it.type == type) &&
            (query.isBlank() || it.name.contains(query, true) || it.number.contains(query, true) || it.notes.contains(query, true))
    }
    // Active cards first, expired last; each group A to Z.
    val active = shown.filter { !it.expired }.sortedBy { it.name.lowercase() }
    val expired = shown.filter { it.expired }.sortedBy { it.name.lowercase() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Solid orange header band; the status bar area above it is orange too.
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(bottom = 8.dp)) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Cards (Open Source)", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                        if (a.syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else IconButton({ a.sync(true) }) { Icon(Icons.Default.Refresh, "Sync") }
                        IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
                        IconButton({ help = true }) { Icon(HelpIcon, "Help") }
                    }
                    if (a.status.isNotEmpty()) Text(a.status, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f))
                }
            }
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Search") }, singleLine = true)
                Box {
                    IconButton({ menu = true }) {
                        Icon(FilterIcon, "Filter", tint = if (type.isEmpty()) LocalContentColor.current else MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("All") }, { type = ""; menu = false }, leadingIcon = { if (type.isEmpty()) Icon(Icons.Default.Check, null) })
                        TYPES.forEach { (k, v) ->
                            DropdownMenuItem({ Text(v) }, { type = k; menu = false }, leadingIcon = { if (type == k) Icon(Icons.Default.Check, null) })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(
                    if (a.store.cards.isEmpty()) "No cards yet. Tap + to add one." else "No matches.",
                    Modifier.padding(32.dp), color = MaterialTheme.colorScheme.outline,
                )
            }
            LazyVerticalGrid(
                GridCells.Fixed(2), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (active.isNotEmpty()) item(key = "h-active", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Active", active.size) }
                gridItems(active, key = { it.id }) { c -> CardTile(c) { onOpen(c.id) } }
                if (expired.isNotEmpty()) item(key = "h-expired", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Expired", expired.size) }
                gridItems(expired, key = { it.id }) { c -> CardTile(c) { onOpen(c.id) } }
            }
        }
        FloatingActionButton(
            onAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp), shape = CircleShape,
            containerColor = Color(0xFFFF8F00), contentColor = Color(0xFF1E1610),
        ) { Icon(Icons.Default.Add, "Add card") }
        if (help) HelpDialog { help = false }
    }
}

/** White text on dark colours, near-black on light ones. */
private fun onColor(c: Int): Color =
    if (androidx.core.graphics.ColorUtils.calculateLuminance(c) > 0.5) Color(0xFF1B1B1F) else Color.White

private fun typeLabel(t: String) = TYPES.firstOrNull { it.first == t }?.second ?: t

private fun masked(n: String) = if (n.length > 4) "•••• " + n.takeLast(4) else n

/** Card-shaped tile for the two-column list. */
@Composable
private fun CardTile(c: Card, onClick: () -> Unit) {
    val ink = onColor(c.color)
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.586f).alpha(if (c.expired) .55f else 1f).clip(RowShape)
            .background(Color(c.color)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(c.name, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Column {
            if (c.number.isNotEmpty() && c.type in listOf("bank", "credit")) {
                Text(masked(c.number), color = ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1)
            }
            Text(
                typeLabel(c.type) + if (c.expired) " · Expired ${c.expiry}" else if (c.expiry.isNotEmpty()) " · ${c.expiry}" else "",
                color = ink.copy(alpha = .75f), fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun CardFace(c: Card) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(CardShape).background(Color(c.color)).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Text(c.name, color = onColor(c.color), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(typeLabel(c.type), color = onColor(c.color).copy(alpha = .75f), fontSize = 13.sp)
        }
        Column {
            if (c.number.isNotEmpty()) Text(grouped(c), color = onColor(c.color), fontFamily = FontFamily.Monospace, fontSize = 19.sp)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(c.holder.uppercase(), color = onColor(c.color).copy(alpha = .9f), fontSize = 14.sp)
                if (c.expiry.isNotEmpty()) Text(
                    (if (c.expired) "EXPIRED " else "") + c.expiry,
                    color = if (c.expired) (if (onColor(c.color) == Color.White) Color(0xFFFFCDD2) else Color(0xFFB71C1C)) else onColor(c.color).copy(alpha = .9f), fontSize = 14.sp, fontWeight = if (c.expired) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun grouped(c: Card) = if (c.type in listOf("bank", "credit")) c.number.filter { !it.isWhitespace() }.chunked(4).joinToString(" ") else c.number

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

private fun copy(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, warn: Boolean = false) {
    if (value.isEmpty()) return
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(!secret) }
    Column(Modifier.fillMaxWidth().clickable { if (secret && !shown) shown = true else copy(ctx, label, value) }.padding(vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        Text(
            if (shown) value else "••••", fontSize = 18.sp, fontFamily = if (label == "Notes") FontFamily.Default else FontFamily.Monospace,
            color = if (warn) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

@Composable
private fun Photo(a: MainActivity, name: String, label: String, onOpen: (Bitmap) -> Unit) {
    if (name.isEmpty()) return
    val bmp = remember(name) { BitmapFactory.decodeFile(a.store.imageFile(name).path) }
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    if (bmp != null) Image(
        bmp.asImageBitmap(), label, Modifier.fillMaxWidth().clip(PhotoShape).clickable { onOpen(bmp) },
        contentScale = ContentScale.FillWidth,
    ) else Text("Image not downloaded yet. Tap Sync.", fontSize = 13.sp)
}

/** Full-screen photo: pinch to zoom, drag to pan, tap anywhere to close. */
@Composable
private fun Viewer(bmp: Bitmap, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val s = (scale * zoom).coerceIn(1f, 8f)
                    val z = s / scale
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var o = (centroid - center) * (1 - z) + off * z + pan
                    val maxX = (s - 1) * size.width / 2f
                    val maxY = (s - 1) * size.height / 2f
                    o = Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                    scale = s
                    off = if (s <= 1f) Offset.Zero else o
                }
            },
    ) {
        Image(
            bmp.asImageBitmap(), null,
            Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun Detail(a: MainActivity, c: Card, onBack: () -> Unit, onEdit: () -> Unit) {
    var viewing by remember { mutableStateOf<Bitmap?>(null) }
    BackHandler { if (viewing != null) viewing = null else onBack() }
    var confirm by remember { mutableStateOf(false) }
    // Full brightness so a barcode scans at the till.
    DisposableEffect(Unit) {
        val w = a.window
        val old = w.attributes.screenBrightness
        w.attributes = w.attributes.also { it.screenBrightness = 1f }
        onDispose { w.attributes = w.attributes.also { it.screenBrightness = old } }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TBtn(onBack) { Text("‹ Back") }
                Spacer(Modifier.weight(1f))
                TBtn(onEdit) { Text("Edit") }
                TBtn({ confirm = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            CardFace(c)
            if (c.barcode.isNotEmpty()) {
                val bmp = remember(c.barcode, c.format) { renderBarcode(c.barcode, c.format) }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(PhotoShape).background(Color.White).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bmp != null) Image(
                        bmp.asImageBitmap(), "Barcode",
                        if (is2D(c.format)) Modifier.fillMaxWidth(.7f).aspectRatio(1f) else Modifier.fillMaxWidth().aspectRatio(3f),
                        contentScale = ContentScale.FillBounds,
                    ) else Text("Can't draw \"${c.barcode}\" as ${c.format}. Edit the card to pick another type.", color = Color.Black)
                    Text(c.barcode, color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Field("Number", c.number); Field("Holder", c.holder)
            Field("Expiry", if (c.expired) "${c.expiry}  (expired)" else c.expiry, warn = c.expired)
            Field("CVV", c.cvv, secret = true); Field("PIN", c.pin, secret = true); Field("Notes", c.notes)
            Photo(a, c.front, "Front") { viewing = it }
            Photo(a, c.back, "Back") { viewing = it }
        }
        viewing?.let { Viewer(it) { viewing = null } }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${c.name}?") },
        text = { Text("It is removed from your cloud storage and from your other phones too.") },
        confirmButton = { TBtn({ confirm = false; a.store.delete(c.id); a.sync(false) }) { Text("Delete") } },
        dismissButton = { TBtn({ confirm = false }) { Text("Cancel") } },
    )
}

private fun fitRect(bmp: Bitmap, box: IntSize): Rect {
    if (box.width == 0 || box.height == 0) return Rect.Zero
    val s = minOf(box.width.toFloat() / bmp.width, box.height.toFloat() / bmp.height)
    val w = bmp.width * s
    val h = bmp.height * s
    return Rect((box.width - w) / 2, (box.height - h) / 2, (box.width + w) / 2, (box.height + h) / 2)
}

/** Drag the corners/edges to crop, drag inside to move the frame. */
@Composable
private fun CropScreen(src: Bitmap, onDone: (Bitmap?) -> Unit) {
    BackHandler { onDone(null) }
    var bmp by remember { mutableStateOf(src) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val fit = remember(bmp, box) { fitRect(bmp, box) }
    var crop by remember(bmp, box) { mutableStateOf(fit) }
    val density = LocalDensity.current
    val grab = with(density) { 44.dp.toPx() }
    val minSide = with(density) { 56.dp.toPx() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.pointerInput(bmp, box) {
                var mode = 0
                detectDragGestures(
                    onDragStart = { p ->
                        val l = abs(p.x - crop.left) < grab; val r = abs(p.x - crop.right) < grab
                        val t = abs(p.y - crop.top) < grab; val b = abs(p.y - crop.bottom) < grab
                        val inX = p.x > crop.left - grab && p.x < crop.right + grab
                        val inY = p.y > crop.top - grab && p.y < crop.bottom + grab
                        mode = if (!(inX && inY)) 0 else {
                            val m = (if (l && !r) 1 else 0) or (if (t && !b) 2 else 0) or (if (r && !l) 4 else 0) or (if (b && !t) 8 else 0)
                            if (m != 0) m else if (crop.contains(p)) 16 else 0
                        }
                    },
                    onDrag = { ch, d ->
                        ch.consume()
                        var (l, t, r, b) = listOf(crop.left, crop.top, crop.right, crop.bottom)
                        if (mode == 16) {
                            val dx = d.x.coerceIn(fit.left - l, fit.right - r)
                            val dy = d.y.coerceIn(fit.top - t, fit.bottom - b)
                            l += dx; r += dx; t += dy; b += dy
                        } else {
                            if (mode and 1 != 0) l = (l + d.x).coerceIn(fit.left, r - minSide)
                            if (mode and 4 != 0) r = (r + d.x).coerceIn(l + minSide, fit.right)
                            if (mode and 2 != 0) t = (t + d.y).coerceIn(fit.top, b - minSide)
                            if (mode and 8 != 0) b = (b + d.y).coerceIn(t + minSide, fit.bottom)
                        }
                        crop = Rect(l, t, r, b)
                    },
                )
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    bmp.asImageBitmap(), dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
                    dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
                )
                val dim = Color.Black.copy(alpha = .62f)
                drawRect(dim, Offset(fit.left, fit.top), Size(fit.width, crop.top - fit.top))
                drawRect(dim, Offset(fit.left, crop.bottom), Size(fit.width, fit.bottom - crop.bottom))
                drawRect(dim, Offset(fit.left, crop.top), Size(crop.left - fit.left, crop.height))
                drawRect(dim, Offset(crop.right, crop.top), Size(fit.right - crop.right, crop.height))
                drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(2.dp.toPx()))
                for (i in 1..2) {
                    val gx = crop.left + crop.width * i / 3; val gy = crop.top + crop.height * i / 3
                    drawLine(Color.White.copy(alpha = .35f), Offset(gx, crop.top), Offset(gx, crop.bottom), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = .35f), Offset(crop.left, gy), Offset(crop.right, gy), 1.dp.toPx())
                }
                for (p in listOf(crop.topLeft, crop.topRight, crop.bottomLeft, crop.bottomRight)) drawCircle(Color.White, 9.dp.toPx(), p)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TBtn({ onDone(null) }) { Text("Cancel", color = Color.White) }
            OBtn({ bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true) }) { Text("Rotate", color = Color.White) }
            TBtn({ crop = fit }) { Text("Reset", color = Color.White) }
            Btn({
                val sx = bmp.width / fit.width
                val x = ((crop.left - fit.left) * sx).toInt().coerceIn(0, bmp.width - 1)
                val y = ((crop.top - fit.top) * sx).toInt().coerceIn(0, bmp.height - 1)
                val w = (crop.width * sx).toInt().coerceIn(1, bmp.width - x)
                val h = (crop.height * sx).toInt().coerceIn(1, bmp.height - y)
                onDone(Bitmap.createBitmap(bmp, x, y, w, h))
            }) { Text("Use") }
        }
    }
}

/** Hue (angle) and saturation (distance from the centre) at full brightness, round with a smooth edge. */
private fun makeWheel(n: Int): Bitmap {
    val px = IntArray(n * n)
    val c = (n - 1) / 2f
    val hsv = floatArrayOf(0f, 0f, 1f)
    for (y in 0 until n) for (x in 0 until n) {
        val dx = x - c; val dy = y - c
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        val a = ((c - d) * 1f + 0.5f).coerceIn(0f, 1f)
        if (a == 0f) continue
        hsv[0] = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        hsv[1] = (d / c).coerceAtMost(1f)
        px[y * n + x] = android.graphics.Color.HSVToColor((a * 255).toInt(), hsv)
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

/** Hue/saturation wheel plus a brightness slider. */
@Composable
private fun ColorPicker(color: Int, onChange: (Int) -> Unit) {
    val init = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var h by remember { mutableFloatStateOf(init[0]) }
    var s by remember { mutableFloatStateOf(init[1]) }
    var v by remember { mutableFloatStateOf(init[2]) }
    val wheel = remember { makeWheel(560) }
    fun push() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    fun pick(p: Offset, size: IntSize) {
        val d = p - Offset(size.width / 2f, size.height / 2f)
        h = ((Math.toDegrees(atan2(d.y, d.x).toDouble()) + 360) % 360).toFloat()
        s = (d.getDistance() / (size.width / 2f)).coerceIn(0f, 1f)
        push()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.size(230.dp)
                .pointerInput(Unit) { detectTapGestures { pick(it, size) } }
                .pointerInput(Unit) { detectDragGestures(onDragStart = { pick(it, size) }, onDrag = { ch, _ -> pick(ch.position, size) }) },
        ) {
            val r = size.width / 2f
            drawImage(wheel.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            drawCircle(Color.Black.copy(alpha = 1f - v), r)
            val rad = Math.toRadians(h.toDouble())
            val at = center + Offset(cos(rad).toFloat(), sin(rad).toFloat()) * (s * r)
            drawCircle(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))), 11.dp.toPx(), at)
            drawCircle(Color.White, 11.dp.toPx(), at, style = Stroke(3.dp.toPx()))
        }
        Slider(v, { v = it; push() }, valueRange = 0f..1f)
        Box(Modifier.fillMaxWidth().height(32.dp).clip(PhotoShape).background(Color(color)))
    }
}

@Composable
private fun Edit(a: MainActivity, original: Card, onDone: (String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = a.store.get(original.id) == null
    var c by remember { mutableStateOf(original) }
    var target by remember { mutableStateOf("front") }
    var captureFile by remember { mutableStateOf<File?>(null) }
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoFilled by remember { mutableStateOf(emptyList<String>()) }

    BackHandler { if (cropping == null) onDone(if (isNew) null else original.id) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { cropping = a.store.decode(it) ?: run { toast(ctx, "Couldn't open that image"); null } }
    }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) captureFile?.let { cropping = a.store.decode(Uri.fromFile(it)); it.delete() }
    }
    fun camera(side: String) {
        target = side
        val f = File(File(ctx.cacheDir, "capture").apply { mkdirs() }, "shot.jpg")
        captureFile = f
        shoot.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    fun gallery(side: String) {
        target = side
        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun scan() {
        GmsBarcodeScanning.getClient(ctx, GmsBarcodeScannerOptions.Builder().build()).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { c = c.copy(barcode = it, format = formatFromScanner(b.format)) } }
            .addOnFailureListener { toast(ctx, "Scan failed: ${it.message}") }
    }
    fun photos() = c.images().mapNotNull { BitmapFactory.decodeFile(a.store.imageFile(it).path) }
    fun expiryFromPhoto() = scope.launch {
        busy = true
        try {
            val e = readExpiry(photos())
            if (e != null) { c = c.copy(expiry = e); autoFilled = (autoFilled + "expiry").distinct() } else toast(ctx, "No expiry date found in the photos")
        } catch (ex: Exception) {
            toast(ctx, "Couldn't read the photos: ${ex.message}")
        }
        busy = false
    }
    fun barcodeFromPhoto() = scope.launch {
        busy = true
        try {
            val r = readBarcode(photos())
            if (r != null) { c = c.copy(barcode = r.first, format = r.second); autoFilled = (autoFilled + "barcode").distinct() } else toast(ctx, "No barcode found in the photos")
        } catch (ex: Exception) {
            toast(ctx, "Couldn't read the photos: ${ex.message}")
        }
        busy = false
    }

    // Fills only the blanks, never what the user typed, and lists what it filled so the user is told to check it.
    fun autoFill(photo: Bitmap, firstPhoto: Boolean) = scope.launch {
        busy = true
        try {
            val g = readCard(photo)
            val done = mutableListOf<String>()
            var n = c
            if (firstPhoto && isNew && g.type != null && (g.number != null || g.barcode != null)) {
                n = n.copy(type = g.type)
                done += "type"
            }
            if (n.number.isBlank() && g.number != null) { n = n.copy(number = g.number); done += "card number" }
            if (n.expiry.isBlank() && g.expiry != null) { n = n.copy(expiry = g.expiry); done += "expiry" }
            if (n.holder.isBlank() && g.holder != null && n.type in listOf("bank", "credit")) { n = n.copy(holder = g.holder); done += "name on card" }
            if (n.barcode.isBlank() && g.barcode != null) { n = n.copy(barcode = g.barcode, format = g.format ?: n.format); done += "barcode" }
            if (n.name.isBlank() && g.brand != null) { n = n.copy(name = g.brand); done += "name" }
            c = n
            if (done.isNotEmpty()) autoFilled = (autoFilled + done).distinct()
        } catch (ex: Exception) {
            toast(ctx, "Couldn't read the photo: ${ex.message}")
        }
        busy = false
    }

    val crop = cropping
    if (crop != null) {
        CropScreen(crop) { out ->
            cropping = null
            if (out != null) {
                val first = c.front.isEmpty() && c.back.isEmpty()
                val name = a.store.saveBitmap(out)
                c = if (target == "front") c.copy(front = name) else c.copy(back = name)
                autoFill(out, first)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TBtn({ onDone(if (isNew) null else original.id) }) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            Btn({ a.store.save(c); a.sync(false); onDone(c.id) }, enabled = c.name.isNotBlank()) { Text("Save") }
        }
        if (autoFilled.isNotEmpty()) Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2), contentColor = Color(0xFF3E2723)), shape = RowShape,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Check these details", fontWeight = FontWeight.Bold)
                Text(
                    "The app filled in " + autoFilled.joinToString(", ") + " from your photo. Reading a photo can get digits, letters " +
                        "and dates wrong. Double-check every detail against your card before you save.",
                    fontSize = 13.sp,
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TYPES) { (k, v) -> FilterChip(c.type == k, { c = c.copy(type = k) }, { Text(v) }) }
        }
        for ((side, label) in listOf("front" to "Front photo", "back" to "Back photo")) {
            val name = if (side == "front") c.front else c.back
            Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            if (name.isNotEmpty()) {
                val bmp = remember(name) { BitmapFactory.decodeFile(a.store.imageFile(name).path) }
                if (bmp != null) Image(bmp.asImageBitmap(), label, Modifier.fillMaxWidth().clip(PhotoShape), contentScale = ContentScale.FillWidth)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OBtn({ camera(side) }) { Text("Camera") }
                OBtn({ gallery(side) }) { Text("Gallery") }
                if (name.isNotEmpty()) TBtn({ c = if (side == "front") c.copy(front = "") else c.copy(back = "") }) { Text("Remove") }
            }
        }
        Txt("Name", c.name) { c = c.copy(name = it) }
        Txt("Card number", c.number, KeyboardType.Number) { c = c.copy(number = it) }
        if (c.type == "bank" || c.type == "credit") {
            Txt("Name on card", c.holder) { c = c.copy(holder = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ExpiryField(c.expiry) { c = c.copy(expiry = it) } }
                Box(Modifier.weight(1f)) { Txt("CVV", c.cvv, KeyboardType.NumberPassword) { c = c.copy(cvv = it) } }
                Box(Modifier.weight(1f)) { Txt("PIN", c.pin, KeyboardType.NumberPassword) { c = c.copy(pin = it) } }
            }
        } else ExpiryField(c.expiry, optional = true) { c = c.copy(expiry = it) }
        OBtn(::expiryFromPhoto, enabled = c.images().isNotEmpty() && !busy) { Text("Read expiry from photo") }

        Text("Barcode", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        Txt("Barcode value", c.barcode) { c = c.copy(barcode = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OBtn(::scan) { Text("Scan") }
            OBtn(::barcodeFromPhoto, enabled = c.images().isNotEmpty() && !busy) { Text("From photo") }
        }
        if (c.number.isNotEmpty() && c.barcode != c.number.filter { !it.isWhitespace() }) {
            OBtn({ c = c.copy(barcode = c.number.filter { !it.isWhitespace() }) }) { Text("Use card number") }
        }
        if (c.barcode.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FORMATS) { (k, v) -> FilterChip(c.format == k, { c = c.copy(format = k) }, { Text(v) }) }
            }
            if (renderBarcode(c.barcode, c.format, 200) == null) Text("This value isn't valid for that barcode type.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Txt("Notes", c.notes, singleLine = false) { c = c.copy(notes = it) }
        Text("Colour", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        ColorPicker(c.color) { c = c.copy(color = it or (0xFF shl 24)) }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun ExpiryField(value: String, optional: Boolean = false, onChange: (String) -> Unit) {
    val parsed = parseExpiry(value)
    val expired = value.isNotEmpty() && parsed != null && Card(expiry = value).expired
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(if (optional) "Expiry (MM/YY, optional)" else "Expiry (MM/YY)") },
        isError = value.isNotEmpty() && (parsed == null || expired),
        supportingText = {
            if (value.isNotEmpty() && parsed == null) Text("Use MM/YY")
            else if (expired) Text("Expired")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun Txt(
    label: String, value: String, kb: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
    secret: Boolean = false, placeholder: String = "", onChange: (String) -> Unit,
) = OutlinedTextField(
    value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
    placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
    visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else kb),
)

private fun storageLabel(a: MainActivity) = when (a.store.storage) {
    "drive" -> "Google Drive" + if (a.store.email.isNotEmpty()) " (${a.store.email})" else ""
    "folder" -> "Cloud folder"
    "s3" -> "Bucket ${a.store.s3.bucket}"
    else -> ""
}

@Composable
private fun SettingsDialog(a: MainActivity, onClose: () -> Unit) {
    var confirmOut by remember { mutableStateOf(false) }
    var lock by remember { mutableStateOf(a.store.lockEnabled) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // "" = not checked, "checking", "error", otherwise the latest version name
    var latest by remember { mutableStateOf("") }
    fun check() {
        latest = "checking"
        scope.launch { latest = latestVersion() ?: "error" }
    }
    LaunchedEffect(Unit) { check() }
    val newer = latest.isNotEmpty() && latest != "checking" && latest != "error" && isNewer(latest, BuildConfig.VERSION_NAME)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved in: " + storageLabel(a))
                Text(
                    if (a.store.lastSync == 0L) "Not synced yet" else "Last sync " + DateUtils.getRelativeTimeSpanString(a.store.lastSync),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OBtn({ a.sync(true) }, enabled = !a.syncing) { Text(if (a.syncing) "Syncing…" else "Sync now") }
                    OBtn({ onClose(); a.status = ""; a.changingStorage = true }) { Text("Change storage") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lock with screen lock")
                        Text("Also blocks screenshots", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(lock, { lock = it; a.setLock(it) })
                }
                Text(
                    "Version ${BuildConfig.VERSION_NAME}: " + when {
                        latest == "checking" -> "checking for updates…"
                        latest == "error" -> "could not check for updates"
                        newer -> "version $latest is available"
                        latest.isNotEmpty() -> "up to date"
                        else -> ""
                    },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OBtn({ check() }, enabled = latest != "checking") { Text("Check for updates") }
                    if (newer) Btn({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))) }) { Text("Update now") }
                }
                TBtn({ confirmOut = true }) { Text("Remove cards from this phone", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TBtn(onClose) { Text("Close") } },
    )
    if (confirmOut) AlertDialog(
        onDismissRequest = { confirmOut = false },
        title = { Text("Remove cards from this phone?") },
        text = { Text("This phone forgets its storage and its cards. The cloud copies stay put and come back when you connect again.") },
        confirmButton = { TBtn({ confirmOut = false; onClose(); a.disconnect() }) { Text("Remove") } },
        dismissButton = { TBtn({ confirmOut = false }) { Text("Cancel") } },
    )
}
