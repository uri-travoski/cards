package au.sam.cards

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File

private sealed interface Screen {
    object Home : Screen
    class Detail(val id: String) : Screen
    class Edit(val card: Card) : Screen
}

@Composable
fun App(a: MainActivity) {
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.systemBarsPadding().imePadding()) {
                when {
                    !a.unlocked -> LockScreen(a)
                    !a.store.signedIn && !a.store.offline -> LoginScreen(a)
                    else -> Cards(a)
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
    Button({ a.requireUnlock() }) { Text("Unlock") }
}

@Composable
private fun LoginScreen(a: MainActivity) = Column(
    Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally,
) {
    Text("Cards", fontSize = 36.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        "Bank, credit and loyalty cards, kept in your own Google Drive. Sign in on any phone to get them all back.",
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Button({ a.signIn() }, Modifier.fillMaxWidth()) { Text("Sign in with Google") }
    TextButton({ a.store.offline = true; a.status = "" }) { Text("Use without an account for now") }
    if (a.status.isNotEmpty()) Text(a.status, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
}

@Composable
private fun Cards(a: MainActivity) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var settings by remember { mutableStateOf(false) }
    when (val s = screen) {
        Screen.Home -> {
            Home(a, onOpen = { screen = Screen.Detail(it) }, onAdd = { screen = Screen.Edit(Card()) }, onSettings = { settings = true })
        }
        is Screen.Detail -> {
            val card = a.store.get(s.id)
            if (card == null) screen = Screen.Home
            else Detail(a, card, onBack = { screen = Screen.Home }, onEdit = { screen = Screen.Edit(card) })
        }
        is Screen.Edit -> Edit(
            a, s.card,
            onDone = { id -> screen = if (id == null || a.store.get(id) == null) Screen.Home else Screen.Detail(id) },
        )
    }
    if (settings) SettingsDialog(a) { settings = false }
}

@Composable
private fun Home(a: MainActivity, onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    val shown = a.store.cards.filter {
        (type.isEmpty() || it.type == type) &&
            (query.isBlank() || it.name.contains(query, true) || it.number.contains(query, true) || it.notes.contains(query, true))
    }.sortedBy { it.name.lowercase() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cards", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (a.syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else if (a.store.signedIn) TextButton({ a.sync(true) }) { Text("Sync") }
                TextButton(onSettings) { Text("Settings") }
            }
            if (a.status.isNotEmpty()) Text(a.status, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search") }, singleLine = true,
            )
            LazyRow(Modifier.padding(bottom = 4.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(type.isEmpty(), { type = "" }, { Text("All") }) }
                items(TYPES) { (k, v) -> FilterChip(type == k, { type = if (type == k) "" else k }, { Text(v) }) }
            }
            if (shown.isEmpty()) {
                Text(
                    if (a.store.cards.isEmpty()) "No cards yet. Tap + to add one." else "No matches.",
                    Modifier.padding(32.dp), color = MaterialTheme.colorScheme.outline,
                )
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.id }) { c -> CardRow(c) { onOpen(c.id) } }
            }
        }
        FloatingActionButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("+", fontSize = 28.sp) }
    }
}

private fun typeLabel(t: String) = TYPES.firstOrNull { it.first == t }?.second ?: t

private fun masked(n: String) = if (n.length > 4) "•••• " + n.takeLast(4) else n

@Composable
private fun CardRow(c: Card, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(14.dp)).background(Color(c.color)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(typeLabel(c.type), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
        }
        if (c.number.isNotEmpty() && c.type in listOf("bank", "credit")) {
            Text(masked(c.number), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        }
    }
}

@Composable
private fun CardFace(c: Card) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(18.dp)).background(Color(c.color)).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Text(c.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(typeLabel(c.type), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
        }
        Column {
            if (c.number.isNotEmpty()) Text(grouped(c), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 19.sp)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(c.holder.uppercase(), color = Color.White.copy(alpha = .9f), fontSize = 14.sp)
                if (c.expiry.isNotEmpty()) Text(c.expiry, color = Color.White.copy(alpha = .9f), fontSize = 14.sp)
            }
        }
    }
}

private fun grouped(c: Card) = if (c.type in listOf("bank", "credit")) c.number.filter { !it.isWhitespace() }.chunked(4).joinToString(" ") else c.number

private fun copy(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    android.widget.Toast.makeText(ctx, "$label copied", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false) {
    if (value.isEmpty()) return
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(!secret) }
    Column(Modifier.fillMaxWidth().clickable { if (secret && !shown) shown = true else copy(ctx, label, value) }.padding(vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        Text(if (shown) value else "••••", fontSize = 18.sp, fontFamily = if (label == "Notes") FontFamily.Default else FontFamily.Monospace)
    }
}

@Composable
private fun Photo(a: MainActivity, name: String, label: String) {
    if (name.isEmpty()) return
    val bmp = remember(name) { BitmapFactory.decodeFile(a.store.imageFile(name).path) }
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    if (bmp != null) Image(bmp.asImageBitmap(), label, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
    else Text("Image not downloaded yet. Tap Sync.", fontSize = 13.sp)
}

@Composable
private fun Detail(a: MainActivity, c: Card, onBack: () -> Unit, onEdit: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirm by remember { mutableStateOf(false) }
    // Full brightness so a barcode scans at the till.
    DisposableEffect(Unit) {
        val w = a.window
        val old = w.attributes.screenBrightness
        w.attributes = w.attributes.also { it.screenBrightness = 1f }
        onDispose { w.attributes = w.attributes.also { it.screenBrightness = old } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onEdit) { Text("Edit") }
            TextButton({ confirm = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        CardFace(c)
        if (c.barcode.isNotEmpty()) {
            val bmp = remember(c.barcode, c.format) { renderBarcode(c.barcode, c.format) }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bmp != null) Image(
                    bmp.asImageBitmap(), "Barcode",
                    if (is2D(c.format)) Modifier.fillMaxWidth(.7f).aspectRatio(1f) else Modifier.fillMaxWidth().aspectRatio(3f),
                    contentScale = ContentScale.FillBounds,
                ) else Text("Can't draw \"${c.barcode}\" as ${c.format}. Edit the card to pick another type.", color = Color.Black)
                Text(c.barcode, color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Field("Number", c.number); Field("Holder", c.holder); Field("Expiry", c.expiry)
        Field("CVV", c.cvv, secret = true); Field("PIN", c.pin, secret = true); Field("Notes", c.notes)
        Photo(a, c.front, "Front"); Photo(a, c.back, "Back")
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${c.name}?") },
        text = { Text("It is removed from your Google Drive and from your other phones too.") },
        confirmButton = { TextButton({ confirm = false; a.store.delete(c.id); a.sync(false) }) { Text("Delete") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Edit(a: MainActivity, original: Card, onDone: (String?) -> Unit) {
    BackHandler { onDone(original.id) }
    val ctx = LocalContext.current
    val isNew = a.store.get(original.id) == null
    var c by remember { mutableStateOf(original) }
    var target by remember { mutableStateOf("front") }
    var captureFile by remember { mutableStateOf<File?>(null) }

    fun setImage(name: String?) {
        if (name == null) return
        c = if (target == "front") c.copy(front = name) else c.copy(back = name)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let { setImage(a.store.importImage(it)) } }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) captureFile?.let { setImage(a.store.importImage(Uri.fromFile(it))); it.delete() }
    }
    fun camera(side: String) {
        target = side
        val f = File(ctx.cacheDir, "capture").apply { mkdirs() }.let { File(it, "shot.jpg") }
        captureFile = f
        shoot.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }
    fun gallery(side: String) {
        target = side
        pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun scan() {
        GmsBarcodeScanning.getClient(ctx, GmsBarcodeScannerOptions.Builder().build()).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { c = c.copy(barcode = it, format = formatFromScanner(b.format)) } }
            .addOnFailureListener { android.widget.Toast.makeText(ctx, "Scan failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onDone(if (isNew) null else original.id) }) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button({ a.store.save(c); a.sync(false); onDone(c.id) }, enabled = c.name.isNotBlank()) { Text("Save") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TYPES) { (k, v) -> FilterChip(c.type == k, { c = c.copy(type = k) }, { Text(v) }) }
        }
        Txt("Name", c.name) { c = c.copy(name = it) }
        Txt("Card number", c.number, KeyboardType.Number) { c = c.copy(number = it) }
        if (c.type == "bank" || c.type == "credit") {
            Txt("Name on card", c.holder) { c = c.copy(holder = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Txt("Expiry (MM/YY)", c.expiry) { c = c.copy(expiry = it) } }
                Box(Modifier.weight(1f)) { Txt("CVV", c.cvv, KeyboardType.NumberPassword) { c = c.copy(cvv = it) } }
                Box(Modifier.weight(1f)) { Txt("PIN", c.pin, KeyboardType.NumberPassword) { c = c.copy(pin = it) } }
            }
        }
        Text("Barcode", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        Txt("Barcode value", c.barcode) { c = c.copy(barcode = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(::scan) { Text("Scan") }
            if (c.number.isNotEmpty() && c.barcode != c.number) OutlinedButton({ c = c.copy(barcode = c.number.filter { !it.isWhitespace() }) }) { Text("Use card number") }
        }
        if (c.barcode.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FORMATS) { (k, v) -> FilterChip(c.format == k, { c = c.copy(format = k) }, { Text(v) }) }
            }
            if (renderBarcode(c.barcode, c.format, 200) == null) Text("This value isn't valid for that barcode type.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Txt("Notes", c.notes, singleLine = false) { c = c.copy(notes = it) }
        Text("Colour", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PALETTE) { p ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(p)).clickable { c = c.copy(color = p) },
                    contentAlignment = Alignment.Center,
                ) { if (c.color == p) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        for ((side, label) in listOf("front" to "Front photo", "back" to "Back photo")) {
            val name = if (side == "front") c.front else c.back
            Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            if (name.isNotEmpty()) {
                val bmp = remember(name) { BitmapFactory.decodeFile(a.store.imageFile(name).path) }
                if (bmp != null) Image(bmp.asImageBitmap(), label, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ camera(side) }) { Text("Camera") }
                OutlinedButton({ gallery(side) }) { Text("Gallery") }
                if (name.isNotEmpty()) TextButton({ c = if (side == "front") c.copy(front = "") else c.copy(back = "") }) { Text("Remove") }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Txt(label: String, value: String, kb: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) =
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
    )

@Composable
private fun SettingsDialog(a: MainActivity, onClose: () -> Unit) {
    var confirmOut by remember { mutableStateOf(false) }
    var lock by remember { mutableStateOf(a.store.lockEnabled) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.store.signedIn) {
                    Text("Signed in as " + a.store.email.ifEmpty { "your Google account" })
                    Text(
                        if (a.store.lastSync == 0L) "Not synced yet" else "Last sync " + DateUtils.getRelativeTimeSpanString(a.store.lastSync),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedButton({ a.sync(true) }, enabled = !a.syncing) { Text(if (a.syncing) "Syncing…" else "Sync now") }
                } else {
                    Text("Cards are only on this phone.")
                    Button({ onClose(); a.signIn() }) { Text("Sign in with Google") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lock with screen lock")
                        Text("Also blocks screenshots", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(lock, { lock = it; a.setLock(it) })
                }
                if (a.store.signedIn) TextButton({ confirmOut = true }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Close") } },
    )
    if (confirmOut) AlertDialog(
        onDismissRequest = { confirmOut = false },
        title = { Text("Sign out?") },
        text = { Text("Cards are removed from this phone only. They stay in your Google Drive and come back when you sign in again.") },
        confirmButton = { TextButton({ confirmOut = false; onClose(); a.signOut() }) { Text("Sign out") } },
        dismissButton = { TextButton({ confirmOut = false }) { Text("Cancel") } },
    )
}
