# SmartTube Material Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Лёгкий интерфейс Android TV на Material 3 поверх движка SmartTube: вкладки сверху, быстрая настройка разделов и кнопок плеера.

**Architecture:** Форк `yuliskov/SmartTube` с новым flavor `stmaterial` в модуле `smarttubetv`. Новые Activity на Compose реализуют существующие интерфейсы `*View` из `common` и регистрируются в `ViewManager`; презентеры и движок не меняются. Правила поведения живут в чистых классах Kotlin без Android и покрыты JVM-тестами, экраны проверяются на эмуляторе скриптами `adb`.

**Tech Stack:** Kotlin 2.4.21, AGP 8.13.2, Gradle 8.14.3, Compose BOM 2026.06.01, `androidx.tv:tv-material:1.1.0`, ExoPlayer из репозитория, Glide из репозитория. Эта связка, кроме `tv-material`, уже собирает этот код в форке SmartTube New.

**Spec:** `docs/superpowers/specs/2026-10-31-tv-client-design.md`

## Global Constraints

- Название: `SmartTube Material`; `applicationId`: `io.github.ivanosh.smarttubematerial`; репозиторий: `ivanosh/SmartTube-Material`.
- `minSdkVersion 29`, `targetSdkVersion 34`, `compileSdkVersion 36`, ABI `arm64-v8a` и `armeabi-v7a`.
- Интерфейс на английском, строки в `smarttubetv/src/stmaterial/res/values/strings.xml`. Русские подписи в мокапах черновые.
- Только пульт: крестовина, ОК, Назад; долгое нажатие ОК — единственный скрытый жест.
- Без дровера. Вкладок не больше 8. На панели плеера 5 слотов и «More».
- По умолчанию вкладки: Subscriptions, Home, History, Playlists, Search; стартовая Subscriptions. Кнопки: Pause, Next, Quality, Subtitles, Speed.
- Настройки нельзя скрыть, и они не могут быть стартовой вкладкой.
- Лёгкость: без размытия, параллакса, автопревью и фоновых задач. Анимируются только фокус и переходы.
- Не входит: каст, голосовой поиск, несколько аккаунтов, комментарии, Shorts, чат, каналы домашнего экрана, перенос данных, встроенное обновление.
- Submodule `MediaServiceCore` и `SharedModules` остаются на URL upstream. Правки в `common` минимальны и помечены комментарием `// MATERIAL:`.
- Точный вид, цвета и компоновка берутся из канваса https://claude.ai/artifact/RnoYTZca2nukxNv3tmAtv6.
- Токены не должны попадать в общее хранилище ни при каких настройках.
- Проверка на эмуляторе: AVD `GoogleTV_34` на `system-images;android-34;google-tv;arm64-v8a`. Выводы о плавности предварительные до проверки на Chromecast.

## Review Focus

1. Пользователь не вошёл, стартовая вкладка Subscriptions: видно понятное приглашение войти, а не пустой экран.
2. Сохранённый порядок ссылается на раздел, которого больше нет: приложение запускается, неизвестный id пропускается, стартовой становится первая вкладка.
3. Нет сети или лента пуста: сообщение и кнопка Retry, фокус остаётся на вкладке, приложение не зависает.
4. Возврат из плеера: фокус на той же карточке и в той же позиции ленты.
5. Быстрые повторные нажатия и удержание крестовины: фокус не теряется, очередь событий не копится, перемотка не накапливается после отпускания.

---

### Task 1: Форк, обновление сборки, flavor `stmaterial`

**Files:**
- Create: `material-constants.gradle`, `smarttubetv/src/stmaterial/AndroidManifest.xml`, `smarttubetv/src/stmaterial/res/values/strings.xml`, `smarttubetv/src/stmaterial/java/io/github/ivanosh/stmaterial/MaterialApplication.kt`, `scripts/emulator.sh`
- Modify: `settings.gradle`, `build.gradle`, `smarttubetv/build.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `.gitignore`
- Copy: spec и этот план в `docs/superpowers/`

**Interfaces:**
- Produces: варианты `stmaterialDebug`, `stmaterialRelease`; `scripts/emulator.sh start|install|shot <file>|key <keycode...>`; `MaterialApplication.registerViews()` — единое место регистрации экранов.

- [ ] **Step 1: Создать форк и клон**

```bash
gh repo fork yuliskov/SmartTube --fork-name SmartTube-Material --clone=false
git clone --recurse-submodules git@github.com:ivanosh/SmartTube-Material.git ~/tv-client/SmartTube-Material
git -C ~/tv-client/SmartTube-Material switch -c material
```
Expected: `git submodule status` показывает `MediaServiceCore` и `SharedModules` без `-`.

- [ ] **Step 2: Подключить слой констант**

`material-constants.gradle` применяет исходный файл констант и переопределяет `kotlinVersion = '2.4.21'`, `compileSdkVersion = 36`, `buildToolsVersion = '36.0.0'`. В `settings.gradle` исходный путь сохраняется в `gradle.ext.upstreamConstants`, а `gradle.ext.sharedModulesConstants` указывает на новый файл, поэтому submodule не меняется.

- [ ] **Step 3: Обновить инструменты и добавить flavor**

Добавить AGP 8.13.2, `compose-compiler-gradle-plugin`, Gradle 8.14.3 и `android.namespace` там, где их требует AGP 8. Flavor `stmaterial`: значения из Global Constraints, `matchingFallbacks = ["stfdroid"]`, `signingConfig signingConfigs.debug` до Task 13. Зависимости `stmaterialImplementation`: Compose BOM, `ui`, `foundation`, `activity-compose:1.11.0`, `tv-material:1.1.0`, `junit:4.13.2` для `testStmaterialImplementation`.

- [ ] **Step 4: Подключить приложение**

`MaterialApplication : MainApplication` с `registerViews()`; в манифесте flavor `android:name` с `tools:replace`. Минимальная правка `MainApplication`, чтобы регистрация переопределялась.

- [ ] **Step 5: Поднять эмулятор и проверить**

```bash
sdkmanager "system-images;android-34;google-tv;arm64-v8a"
scripts/emulator.sh start && ./gradlew :smarttubetv:installStmaterialDebug
adb shell pm list packages | grep smarttubematerial
```
Expected: пакет установлен, приложение открывается со старым интерфейсом, в APK нет классов `com.google.firebase` и `crashlytics`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "build: add stmaterial flavor on AGP 8 and Compose"
```

---

### Task 2: Защита токена и закрытие поверхностей

**Files:**
- Create: `common/src/main/java/.../common/misc/BackupExclusions.java`, `common/src/test/java/.../misc/BackupExclusionsTest.java`
- Modify: `common/.../misc/BackupAndRestoreManager.java`, `common/.../prefs/GeneralData.java:656`, `common/.../misc/LocalDriveBackupWorker.java:59-60`, `smarttubetv/src/stmaterial/AndroidManifest.xml`

**Interfaces:**
- Produces: `BackupExclusions.isSignInFile(File): Boolean`.

- [ ] **Step 1: Написать падающие тесты**

`isSignInFile` возвращает `true` для `global_prefs/media_service_account_data`, `global_prefs/oauth2_account_data`; `false` для `global_prefs/media_service_data`. Тест на то, что `cancel()` снимает задание при частоте `-1`.

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :common:testStfdroidDebugUnitTest --tests '*BackupExclusionsTest*'`
Expected: FAIL, класс не определён.

- [ ] **Step 3: Реализовать**

`BackupExclusions` как в NewTube, MIT. Фильтр в обоих вызовах `FileHelpers.copy` в `backupToDir`. Авто-бэкап по умолчанию выключен (`-1`). `cancel()` работает при любой частоте, `doWork()` проверяет настройку. В манифесте flavor: `allowBackup="false"`, `BackupReceiverActivity` с `tools:node="remove"`, пустой `update_urls`.

- [ ] **Step 4: Проверить**

Run: тесты из Step 1. Затем на эмуляторе: `adb shell ls /sdcard/Documents/SmartTubeBackup` после запуска.
Expected: PASS; каталога нет.

- [ ] **Step 5: Commit** `fix(security): keep tokens out of backups and close backup receiver`

---

### Task 3: Модель редактора

Общая логика для разделов и кнопок.

**Files:**
- Create: `smarttubetv/src/stmaterial/java/io/github/ivanosh/stmaterial/logic/EditorModel.kt`
- Test: `smarttubetv/src/testStmaterial/java/io/github/ivanosh/stmaterial/logic/EditorModelTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class EditorState<T>(val shown: List<T>, val hidden: List<T>, val locked: Set<T>, val limit: Int, val picked: T? = null)
data class EditResult<T>(val state: EditorState<T>, val displaced: T? = null)
fun <T> EditorState<T>.pick(item: T): EditorState<T>
fun <T> EditorState<T>.moveBy(delta: Int): EditorState<T>
fun <T> EditorState<T>.toggleList(): EditResult<T>
fun <T> EditorState<T>.reset(defaults: EditorState<T>): EditorState<T>
```

- [ ] **Step 1: Написать падающие тесты**

`moveBy` меняет порядок и останавливается на границах. `toggleList` на лимите возвращает последний незаблокированный в `displaced` и кладёт его в `hidden`. Последний видимый пункт скрыть нельзя. Заблокированный нельзя скрыть. `reset` восстанавливает значения. Счётчик не превышает `limit`.

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :smarttubetv:testStmaterialDebugUnitTest --tests '*EditorModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Реализовать `EditorModel.kt`**

- [ ] **Step 4: Запустить и увидеть успех**

- [ ] **Step 5: Commit** `feat: add shared editor model`

---

### Task 4: Хранилище разделов

**Files:**
- Create: `.../stmaterial/logic/Sections.kt`, `.../stmaterial/data/SectionStore.kt`
- Test: `.../logic/SectionsTest.kt`

**Interfaces:**
- Consumes: `EditorState`.
- Produces:
```kotlin
object Sections { val DEFAULT_SHOWN: List<Int>; const val LIMIT = 8 }
fun resolveTabs(saved: List<Int>, known: Set<Int>): List<Int>
fun resolveStart(saved: Int, tabs: List<Int>): Int
class SectionStore(context: Context) { fun load(): EditorState<Int>; fun save(state: EditorState<Int>); fun startSection(): Int; fun setStart(id: Int) }
```

`SectionStore` — обёртка над `SidebarService`: `enableSection`, `orderSections`, `getBootSectionId`, `setBootSectionId`. Значения по умолчанию задаются при первом запуске, а скрытые разделы — те, что не исключены из первой версии.

- [ ] **Step 1: Написать падающие тесты**

`resolveTabs` пропускает неизвестные id и сохраняет порядок (Review Focus 2). `resolveStart`: скрытая или неизвестная стартовая вкладка заменяется первой, Settings стартовой не бывает. Для пустого сохранения — `DEFAULT_SHOWN`.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать `Sections.kt` и `SectionStore.kt`**
- [ ] **Step 4: Запустить и увидеть успех**
- [ ] **Step 5: Commit** `feat: resolve tabs and start section`

---

### Task 5: Тема и главный экран с вкладками

Первый видимый результат и прототип ленты.

**Files:**
- Create: `.../stmaterial/ui/Theme.kt`, `.../ui/Tabs.kt`, `.../ui/VideoCard.kt`, `.../ui/Feed.kt`, `.../ui/ErrorState.kt`, `.../browse/BrowseState.kt`, `.../browse/MaterialBrowseActivity.kt`, `.../browse/BrowseScreen.kt`
- Modify: `MaterialApplication.kt`, `stmaterial/AndroidManifest.xml`
- Test: `.../browse/BrowseStateTest.kt`

**Interfaces:**
- Consumes: `resolveTabs`, `BrowsePresenter`, `BrowseView`, `SectionStore`.
- Produces: `class MaterialBrowseActivity : LeanbackActivity(), BrowseView`; `class BrowseState { sections; selected; rows: List<VideoRow>; focused: FocusKey?; error: ErrorData? }`; `data class FocusKey(rowId: String, videoId: String)`; `@Composable fun VideoCard(video, focused, onClick, onLongClick)`.

Образец подключения: `NovaBrowseActivity` в `margantcovka/SmartTube-New` (MIT). Жизненный цикл презентера повторяет его. Вызов `onScrollEnd` не чаще одного раза на размер ряда.

- [ ] **Step 1: Написать падающие тесты `BrowseState`**

`updateSection` с `ACTION_APPEND` дописывает, `ACTION_REPLACE` заменяет, `ACTION_REMOVE` убирает. Смена вкладки очищает контент. `restoreFocus` возвращает сохранённый `FocusKey` или первую карточку, если видео исчезло (Review Focus 4). Ошибка не сбрасывает выбранную вкладку (Review Focus 3).

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать состояние, тему и экран**

Вкладки уезжают при движении вниз и возвращаются вверх. Превью Glide в размере 400×225, без предзагрузки дальше двух экранов. Значок стартовой вкладки как на канвасе. Показ приглашения войти на вкладках, требующих аккаунт (Review Focus 1). Настройки — значок справа.

- [ ] **Step 4: Запустить и увидеть успех**

- [ ] **Step 5: Проверить на эмуляторе**

```bash
./gradlew :smarttubetv:installStmaterialDebug && scripts/emulator.sh start
scripts/emulator.sh key 20 20 22 22 22 19 && scripts/emulator.sh shot browse.png
adb shell dumpsys gfxinfo io.github.ivanosh.smarttubematerial | grep -E 'Janky|percentile'
```
Expected: приложение открывается на Subscriptions; 30 быстрых нажатий вправо не теряют фокус (Review Focus 5); записать процент janky-кадров в `docs/perf.md` как предварительный результат. Если выше 10%, упростить карточки до единого битмапа, как `ShelfRenderer` в SmartTube New.

- [ ] **Step 6: Commit** `feat: add tabbed browse screen`

---

### Task 6: Меню вкладки и редактор разделов

**Files:**
- Create: `.../ui/ContextMenu.kt`, `.../ui/ListEditor.kt`, `.../browse/MaterialSectionsEditorActivity.kt`
- Modify: `BrowseScreen.kt`, `MaterialBrowseActivity.kt`
- Test: `.../ui/ListEditorKeysTest.kt`

**Interfaces:**
- Consumes: `EditorState`, `SectionStore`.
- Produces: `@Composable fun ContextMenu(items: List<MenuItem>, onDismiss)`; `data class MenuItem(label: String, onClick: () -> Unit)`; `@Composable fun <T> ListEditor(state, label: (T) -> String, onChange, onDone, preview: @Composable () -> Unit = {})`; `fun <T> editorKey(state, key: EditorKey): EditResult<T>`.

- [ ] **Step 1: Написать падающие тесты `editorKey`**

ОК берёт и отпускает пункт; вверх-вниз с взятым пунктом меняют порядок, без него двигают курсор; влево-вправо переносят; Назад возвращает состояние для сохранения.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать меню и редактор**

Меню по долгому ОК: `Open first`, `Hide`, `Customize sections`. У Settings только третий пункт. При скрытии стартовой вкладки — короткое сообщение. Счётчик `6 of 8`, сброс `Reset to default`, нижняя подсказка с действиями пульта. Пункт в Настройках добавляется в Task 12.

- [ ] **Step 4: Запустить тесты и проверить на эмуляторе**

Run: скрипт: долгое ОК (`adb shell input keyevent --longpress 23`), `Open first`, перезапуск приложения.
Expected: тесты PASS; приложение открылось на новой стартовой вкладке, порядок сохранён после перезапуска.

- [ ] **Step 5: Commit** `feat: add tab menu and section editor`

---

### Task 7: Ряды вкладки Subscriptions

**Files:**
- Create: `.../logic/SubscriptionRows.kt`
- Modify: `BrowseState.kt`
- Test: `.../logic/SubscriptionRowsTest.kt`

**Interfaces:**
- Produces: `fun buildRows(subscriptions: List<VideoItem>, history: List<VideoItem>): List<RowModel>`; `data class VideoItem(id: String, progressPct: Int)`; `data class RowModel(id: String, titleRes: Int, ids: List<String>)`.

- [ ] **Step 1: Написать падающие тесты**

`Continue watching` содержит видео с прогрессом от 5 до 95 включительно. Видео из него не повторяется в `New in subscriptions`. Пустой ряд не создаётся. Пустая история даёт один ряд.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать**

Историю получать через `BrowsePresenter`/`ContentService` без изменений движка. Если для этого потребуется правка `MediaServiceCore`, применить запасной вариант из спеки: плоская сетка с рядом `Continue watching` сверху.

- [ ] **Step 4: Запустить и увидеть успех**
- [ ] **Step 5: Commit** `feat: build subscription rows on the client`

---

### Task 8: Меню карточки видео

**Files:**
- Create: `.../browse/VideoMenu.kt`
- Modify: `BrowseScreen.kt`, `MaterialBrowseActivity.kt`
- Test: `.../browse/VideoMenuTest.kt`

**Interfaces:**
- Consumes: `ContextMenu`, `MenuItem`.
- Produces: `fun videoMenuActions(section: Int): List<VideoAction>`; `enum class VideoAction { QUEUE, PLAYLIST, CHANNEL, NOT_INTERESTED, BLOCK_CHANNEL, REMOVE_FROM_HISTORY }`.

- [ ] **Step 1: Написать падающий тест**

`REMOVE_FROM_HISTORY` только на History; остальные пять везде, в порядке из спеки.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать меню, действия отдать существующим сервисам `common`**

Найти обработчики в `VideoMenuPresenter`, новых вызовов YouTube не добавлять.

- [ ] **Step 4: Запустить тест, проверить на эмуляторе без аккаунта**

Expected: PASS; меню открывается, Назад возвращает фокус на карточку.

- [ ] **Step 5: Commit** `feat: add video card menu`

---

### Task 9: Общий диалог

**Files:**
- Create: `.../dialog/MaterialDialogActivity.kt`, `.../dialog/DialogScreen.kt`, `.../dialog/DialogState.kt`
- Modify: `MaterialApplication.kt`, манифест
- Test: `.../dialog/DialogStateTest.kt`

**Interfaces:**
- Consumes: `AppDialogView`, `AppDialogPresenter`, `OptionCategory`, `OptionItem`.
- Produces: `class MaterialDialogActivity : Activity, AppDialogView`; прозрачное наложение над плеером.

- [ ] **Step 1: Написать падающие тесты `DialogState`**

Radio-категория держит один выбранный пункт, checkbox переключается, стек возвращается на предыдущую страницу, пустая категория не показывается.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать один компонент для всех категорий**

Поддержать типы из `OptionCategory`; неизвестный тип показать как простой список. Строки upstream на языке устройства остаются как есть.

- [ ] **Step 4: Запустить тесты, проверить на эмуляторе**

Expected: окно настроек старого меню открывается новым стилем, Назад закрывает без потери видео.

- [ ] **Step 5: Commit** `feat: render app dialogs with one compose component`

---

### Task 10: Плеер

**Files:**
- Create: `.../logic/PlayerKeys.kt`, `.../player/MaterialPlaybackActivity.kt`, `.../player/PlayerOverlay.kt`, `.../player/PlayerOverlayState.kt`, `.../player/PlayerCommands.kt`
- Modify: `MaterialApplication.kt`, манифест
- Test: `.../logic/PlayerKeysTest.kt`

**Interfaces:**
- Consumes: `PlaybackActivity`, `PlaybackFragment`, `PlayerEngine`, `PlaybackPresenter` (реализует `PlayerEventListener`).
- Produces:
```kotlin
enum class PanelFocus { HIDDEN, PROGRESS, BUTTONS }
data class PlayerUi(val focus: PanelFocus, val buttonIndex: Int, val buttonCount: Int)
sealed interface PlayerCommand { object TogglePlay; data class Seek(val deltaMs: Long); data class Press(val index: Int); object Exit; object OpenMore }
fun playerKey(ui: PlayerUi, key: PlayerKey, seekStepMs: Long): Pair<PlayerUi, List<PlayerCommand>>
```

Движок и `PlaybackFragment` остаются; элементы Leanback скрыты. Активность перехватывает клавиши в `dispatchKeyEvent` и передаёт в `playerKey`; Compose только рисует, поэтому фокус не теряется. Образец: `NovaPlaybackActivity` в SmartTube New.

- [ ] **Step 1: Написать падающие тесты `playerKey`**

Панель скрыта: ОК → `TogglePlay` и `BUTTONS`; влево-вправо → `Seek`; вверх-вниз → `BUTTONS`; Назад → `Exit`. Панель открыта: вверх → `PROGRESS`; на прогрессе влево-вправо → `Seek`; Назад скрывает панель без `Exit`; индекс кнопки останавливается на границах. Повтор клавиши даёт один `Seek` на событие (Review Focus 5).

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать `PlayerKeys.kt`, оверлей и исполнение команд**

Прогресс с главами и отметками SponsorBlock как на канвасе, данные из `PlayerUI`/`SeekBarSegment`. Медиакнопки и цифры передаются фрагменту без изменений.

- [ ] **Step 4: Запустить тесты, проверить на эмуляторе**

Expected: видео открывается, управление с пульта работает по таблице, Назад из плеера возвращает на ту же карточку (Review Focus 4).

- [ ] **Step 5: Commit** `feat: add compose player overlay`

---

### Task 11: Кнопки плеера, «More» и редактор

**Files:**
- Create: `.../logic/PlayerButtons.kt`, `.../data/ButtonStore.kt`, `.../player/PlayerMoreSheet.kt`, `.../player/MaterialButtonsEditorActivity.kt`
- Modify: `PlayerOverlay.kt`, `PlayerCommands.kt`
- Test: `.../logic/PlayerButtonsTest.kt`

**Interfaces:**
- Consumes: `EditorState`, `ListEditor`, `PlayerCommand`.
- Produces: `enum class PlayerButton`; `val DEFAULT_BUTTONS`; `fun moreGroups(shown: List<PlayerButton>): List<ButtonGroup>`; `class ButtonStore(context) { load(); save(state) }`.

`PlayerButton` содержит кнопки первой версии: Play/Pause, Next, Previous, Quality, Subtitles, Speed, Like, Dislike, Subscribe, Channel, Add to playlist, Queue, Repeat, Search, Video info, Share, Zoom, Stats, Seek interval, SponsorBlock, Screen dimming, Mute, AFR. Группировка по смыслу вместо черновой: Playback, Picture and sound, Video and channel.

- [ ] **Step 1: Написать падающие тесты**

Умолчание равно Global Constraints. Лимит 5. На лимите вытесненная кнопка появляется в `moreGroups`. Кнопки на панели не дублируются в «More». Каждая `PlayerButton` входит ровно в одну группу. Неизвестное сохранённое значение пропускается.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать**

Каждая `PlayerButton` вызывает соответствующий метод `PlayerEventListener`. Последний пункт «More» — `Customize buttons`. Редактор использует `ListEditor` с `preview`, счётчик `5 of 5`, сообщение о вытеснении и `Reset to default`.

- [ ] **Step 4: Запустить тесты, проверить на эмуляторе**

Expected: PASS; изменение порядка видно в панели сразу и сохраняется после перезапуска.

- [ ] **Step 5: Commit** `feat: add customizable player buttons`

---

### Task 12: Поиск, канал, вход, настройки

**Files:**
- Create: `.../search/MaterialSearchActivity.kt`, `.../search/Keyboard.kt`, `.../channel/MaterialChannelActivity.kt`, `.../signin/MaterialSignInActivity.kt`, `.../settings/MaterialSettings.kt`, `.../logic/SearchInput.kt`
- Modify: `MaterialApplication.kt`, манифест
- Test: `.../logic/SearchInputTest.kt`

**Interfaces:**
- Consumes: `SearchView`, `ChannelView`, `SignInView`, `Feed`, `VideoCard`, `ListEditor`.
- Produces: регистрация всех экранов первой версии в `registerViews()`.

- [ ] **Step 1: Написать падающие тесты `SearchInput`**

Ввод, удаление, пробел, ограничение длины, пустой запрос не запускает поиск.

- [ ] **Step 2: Запустить и увидеть падение**
- [ ] **Step 3: Реализовать экраны**

Вход показывает код и адрес, без QR на внешнем сервисе. Настройки — короткие страницы: аккаунт (вход и выход), `Customize sections`, блокировка рекламных вставок, качество по умолчанию, Blocked channels, About с MIT-уведомлением SmartTube. При выходе напомнить, что доступ отзывается в настройках Google-аккаунта. Глобальные разделы настроек upstream доступны через диалог Task 9 только там, где нужны.

- [ ] **Step 4: Запустить тесты и проверить сценарий на эмуляторе**

Expected: поиск, открытие канала, показ кода входа. Вход в аккаунт подтверждает владелец сам.

- [ ] **Step 5: Commit** `feat: add search, channel, sign-in and settings screens`

---

### Task 13: Сборка релиза и CI

**Files:**
- Create: `.github/workflows/material.yml`, `docs/perf.md`, `README-Material.md`
- Modify: `smarttubetv/build.gradle`, workflow upstream отключить

- [ ] **Step 1: Настроить подпись**

Сгенерировать ключ, хранить вне репозитория, передать в `gh secret set`. Подпись `stmaterialRelease` из переменных окружения.

- [ ] **Step 2: Добавить workflow**

Actions закреплены по SHA, без `pull_request_target`, тесты и сборка `assembleStmaterialRelease`, артефакт и attestation APK на тег `material-v*`.

- [ ] **Step 3: Проверить**

Run: `./gradlew :smarttubetv:testStmaterialDebugUnitTest :smarttubetv:assembleStmaterialRelease`, затем `apksigner verify --print-certs` и установка на эмулятор.
Expected: PASS; APK подписан новым ключом; в APK нет Firebase; `gh run list` зелёный.

- [ ] **Step 4: Записать ограничения**

`README-Material.md`: как собрать, поставить через `adb`, что производительность на Chromecast ещё не измерена.

- [ ] **Step 5: Commit** `ci: build and attest material releases`

После этого — замер на Chromecast, как только появится доступ: `docs/perf.md`, тот же скрипт из Task 5.
