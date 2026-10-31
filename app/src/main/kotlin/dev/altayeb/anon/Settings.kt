@file:OptIn(ExperimentalMaterial3Api::class)

package dev.altayeb.anon

import android.content.Context
import android.Manifest
import android.app.LocaleManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BrandDark = darkColorScheme(
    background = Color(0xFF101112),
    surface = Color(0xFF1B1B1C),
    surfaceContainer = Color(0xFF2C2D2F),
    primary = Color(0xFF33B5E5),
    onPrimary = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFFA9ACB1),
)

/** Settings screen. Only this Activity uses Compose; the keyboard service stays dependency-free. */
class Settings : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = BrandDark) {
                SettingsScreen(prefs)
            }
        }
    }
}

@Composable
private fun SettingsScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val d = remember { Prefs() }
    val toggles = remember { mutableStateMapOf<String, Boolean>() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val done = stringResource(R.string.done)
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var typed by remember { mutableStateOf("") }
    var height by remember { mutableIntStateOf(prefs.getInt("keyHeight", d.keyHeight).coerceIn(80, 140)) }
    var hand by remember { mutableIntStateOf(prefs.getInt("oneHanded", d.oneHanded)) }
    var askLicenses by remember { mutableStateOf(false) }
    var askWords by remember { mutableStateOf(false) }
    var askClips by remember { mutableStateOf(false) }

    fun flag(key: String, def: Boolean) = toggles[key] ?: prefs.getBoolean(key, def)
    fun setFlag(key: String, on: Boolean) {
        toggles[key] = on
        prefs.edit().putBoolean(key, on).apply()
    }
    fun wipe(key: String) {
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        scope.launch { snack.showSnackbar(done) }
    }
    fun tell(msg: Int) { val text = context.getString(msg); scope.launch { snack.showSnackbar(text) } }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { Backup.write(it, java.io.File(context.filesDir, "user.dict"), prefs.all) }
            }.isSuccess
            tell(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                val data = context.contentResolver.openInputStream(uri)!!.use { Backup.read(it, java.io.File(context.cacheDir, "restore.dict")) }
                val edit = prefs.edit()
                Backup.applyPrefs(data.prefs, edit)
                data.dict?.let { java.nio.file.Files.move(it.toPath(), java.io.File(context.filesDir, "user.dict").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                edit.putLong(Backup.RELOAD_KEY, System.currentTimeMillis()).apply()
            }.isSuccess
            tell(if (ok) R.string.backup_restored else R.string.backup_bad_file)
        }
    }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        setFlag("contacts", it) // denied keeps it off
    }

    @Composable
    fun Flag(key: String, def: Boolean, title: Int, sub: Int) =
        SwitchRow(title, sub, flag(key, def)) { setFlag(key, it) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scroll) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(
            contentPadding = pad + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.enable_keyboard)) }
                        FilledTonalButton(
                            onClick = { context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.choose_keyboard)) }
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = { Text(stringResource(R.string.try_typing)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Group(R.string.group_smart) {
                    Flag("suggestions", d.suggestions, R.string.suggestions, R.string.suggestions_sub)
                    Flag("predictNext", d.predictNext, R.string.predict_next, R.string.predict_next_sub)
                    Flag("autocorrect", d.autocorrect, R.string.autocorrect, R.string.autocorrect_sub)
                    Flag("learn", d.learn, R.string.learn, R.string.learn_sub)
                    SwitchRow(R.string.contacts, R.string.contacts_sub, flag("contacts", d.contacts)) {
                        if (it) contacts.launch(Manifest.permission.READ_CONTACTS) else setFlag("contacts", false)
                    }
                }
            }
            item {
                Group(R.string.group_clipboard) {
                    Flag("clipboard", d.clipboard, R.string.clipboard, R.string.clipboard_sub)
                }
            }
            item {
                Group(R.string.group_privacy) {
                    Flag("incognito", d.incognito, R.string.incognito, R.string.incognito_sub)
                    Flag("clearClipsOnLock", d.clearClipsOnLock, R.string.clear_on_lock, R.string.clear_on_lock_sub)
                    Flag("secureKeyboard", d.secureKeyboard, R.string.secure_keyboard, R.string.secure_keyboard_sub)
                    ActionRow(R.string.backup_save) { saveBackup.launch("hudhud-backup.zip") }
                    ActionRow(R.string.backup_restore) { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) }
                    ActionRow(R.string.clear_words) { askWords = true }
                    ActionRow(R.string.clear_clips) { askClips = true }
                }
            }
            item {
                Group(R.string.group_typing) {
                    Flag("glide", d.glide, R.string.glide, R.string.glide_sub)
                    Flag("swipeEdit", d.swipeEdit, R.string.swipe_edit, R.string.swipe_edit_sub)
                    Flag("autoCaps", d.autoCaps, R.string.auto_caps, R.string.auto_caps_sub)
                    Flag("doubleSpacePeriod", d.doubleSpacePeriod, R.string.double_space, R.string.double_space_sub)
                    Flag("vibrate", d.vibrate, R.string.vibrate, R.string.vibrate_sub)
                    Flag("sound", d.sound, R.string.sound, R.string.sound_sub)
                }
            }
            item { LookGroup(d) }
            item {
                Group(R.string.group_languages) { LanguageChoice(prefs, d.languages) }
            }
            item {
                Group(R.string.group_layout) {
                    Flag("split", d.split, R.string.split, R.string.split_sub)
                    SwitchRow(R.string.one_handed, R.string.one_handed_sub, hand != 0) {
                        hand = if (it) 1 else 0
                        prefs.edit().putInt("oneHanded", hand).apply()
                    }
                    Flag("numberRow", d.numberRow, R.string.number_row, R.string.number_row_sub)
                    Flag("hints", d.hints, R.string.hints, R.string.hints_sub)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.key_height), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.percent, height),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Slider(
                            value = height.toFloat(),
                            onValueChange = {
                                height = it.roundToInt()
                                prefs.edit().putInt("keyHeight", height).apply()
                            },
                            valueRange = 80f..140f,
                            steps = 11,
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) item { LanguageGroup() }
            item {
                Group(R.string.group_about) {
                    ActionRow(R.string.licenses) { askLicenses = true }
                    Text(
                        stringResource(R.string.about_text),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.credit_text),
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.version, version),
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (askLicenses) {
        val text = remember {
            runCatching { listOf("NOTICE.txt", "LICENSE.txt").joinToString("\n\n") { n -> context.assets.open(n).bufferedReader().use { it.readText() } } }.getOrDefault("")
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askLicenses = false },
            title = { Text(stringResource(R.string.licenses)) },
            text = { Text(text, Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { askLicenses = false }) { Text(stringResource(R.string.licenses_close)) } },
        )
    }
    if (askWords) ConfirmDialog(R.string.confirm_words_title, R.string.confirm_words_text,
        onConfirm = { wipe("wipeLearnedAt"); askWords = false }, onDismiss = { askWords = false })
    if (askClips) ConfirmDialog(R.string.confirm_clips_title, R.string.confirm_clips_text,
        onConfirm = { wipe("wipeClipsAt"); askClips = false }, onDismiss = { askClips = false })
}

/** Colours and motion of the keyboard. Classic (the default) keeps the original look; sizes never change. */
@Composable
private fun LookGroup(d: Prefs) {
    val prefs = LocalContext.current.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    var theme by remember { mutableStateOf(prefs.getString("theme", d.theme) ?: d.theme) }
    var anim by remember { mutableStateOf(prefs.getBoolean("animations", d.animations)) }
    Group(R.string.group_look) {
        run {
            val tags = listOf("classic", "light", "material")
            val labels = listOf(R.string.theme_classic, R.string.theme_light, R.string.theme_material)
            Text(stringResource(R.string.theme), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(8.dp)) {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = theme == tags[i],
                        onClick = { theme = tags[i]; prefs.edit().putString("theme", theme).apply() },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        ColorChoice(R.string.accent, ACCENTS, prefs.getInt("accent", d.accent)) { prefs.edit().putInt("accent", it).apply() }
        ToneChoice(prefs)
        SwitchRow(R.string.animations, R.string.animations_sub, anim) {
            anim = it; prefs.edit().putBoolean("animations", it).apply()
        }
    }
}

/** Languages the space bar switches between. At least one always stays on. */
@Composable
private fun LanguageChoice(prefs: SharedPreferences, default: String) {
    var on by remember { mutableStateOf(Layouts.enabled(prefs.getString("languages", default) ?: default)) }
    Layouts.ALL.forEach { (id, name) ->
        Row(
            Modifier.fillMaxWidth().clickable {
                val next = if (id in on) on - id else on + id
                if (next.isNotEmpty()) {
                    on = next
                    prefs.edit().putString("languages", next.joinToString(",")).apply()
                }
            }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.Checkbox(checked = id in on, onCheckedChange = null)
        }
    }
}

private val ACCENTS = listOf(0, 0xFF71E2FB.toInt(), 0xFFF2A65A.toInt(), 0xFF7BD88F.toInt(), 0xFFFF7A90.toInt(), 0xFFC3A6FF.toInt())

@Composable
private fun ColorChoice(title: Int, colors: List<Int>, start: Int, onPick: (Int) -> Unit) {
    var sel by remember { mutableIntStateOf(start) }
    Text(stringResource(title), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { c ->
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (c == 0) Color.Gray else Color(c))
                    .then(if (sel == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(18.dp)) else Modifier)
                    .clickable { sel = c; onPick(c) },
            )
        }
    }
}

@Composable
private fun ToneChoice(prefs: SharedPreferences) {
    var tone by remember { mutableIntStateOf(prefs.getInt("emojiTone", 0)) }
    val hands = listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿")
    Text(stringResource(R.string.emoji_tone), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        hands.forEachIndexed { i, h ->
            Text(
                h,
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (tone == i) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { tone = i; prefs.edit().putInt("emojiTone", i).apply() }.padding(6.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun LanguageGroup() {
    val lm = LocalContext.current.getSystemService(LocaleManager::class.java)
    val tags = listOf("", "en", "ar")
    val labels = listOf(R.string.lang_system, R.string.lang_en, R.string.lang_ar)
    var sel by remember {
        mutableIntStateOf(tags.indexOf(lm.applicationLocales.toLanguageTags().substringBefore('-')).coerceAtLeast(0))
    }
    Group(R.string.group_language) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(8.dp)) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = sel == i,
                    onClick = {
                        sel = i
                        lm.applicationLocales = LocaleList.forLanguageTags(tags[i])
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun Group(title: Int, content: @Composable ColumnScope.() -> Unit) {
    // Same entrance as the keyboard's panels: Motion.NORMAL, decelerate, small rise.
    val ctx = LocalContext.current
    val motion = remember { Motion.enabled(Prefs.read(ctx).animations) }
    val shown = remember { MutableTransitionState(!motion) }.apply { targetState = true }
    val spec = tween<Float>(Motion.NORMAL.toInt(), easing = EnterEasing)
    AnimatedVisibility(
        shown,
        enter = fadeIn(spec) + slideInVertically(tween(Motion.NORMAL.toInt(), easing = EnterEasing)) { it / 10 },
    ) { GroupBody(title, content) }
}

/** Motion.DECELERATE as a Compose easing. */
private val EnterEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

@Composable
private fun GroupBody(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            stringResource(title),
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(title: Int, sub: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onChange(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp).animateContentSize(spring()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = if (checked) { { Text("✓", style = MaterialTheme.typography.labelSmall) } } else null,
        )
    }
}

@Composable
private fun ActionRow(title: Int, onClick: () -> Unit) {
    Text(
        stringResource(title),
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(16.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ConfirmDialog(title: Int, text: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
