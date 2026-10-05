# PrimeGram — AyuGram feature port status

Base: **Telegram Android 12.10.6 (7112)**, clean `DrKLO/Telegram` checkout.
Reference: **AyuGramDesktop v7.0.9** (`Telegram/SourceFiles/ayu/`, ~68 000 LOC C++).

Everything ported lives under `com.radolyn.ayugram` so it stays isolated from the
Telegram tree and future upstream merges stay cheap. Integration with Telegram
itself is limited to a handful of one-line hooks (see "Upstream patches").

---

## What is done

### Tier 1 — foundation

| File | Ported from | Notes |
|---|---|---|
| `AyuConfig.java` | `ayu_settings.h` (`AyuSettings`) | ~35 settings, SharedPreferences-backed, `load()` / `save()` / `reload()`, plus `shouldSaveDeletedMessage` / `shouldSaveEditedMessage` helpers |
| `AyuConstants.java` | — | product name, prefs/db names, ghost-mode broadcast |
| `GhostMode.java` | `ayu_settings.h` (`GhostModeAccountSettings`) | per-account, two layers: user flags + `*Locked` overrides, `SendWithoutSound` enum |
| `AyuState.java` | `ayu_state.cpp` | locally-hidden message set, survives rotation |
| `AyuHooks.java` | — | the single seam Telegram calls into; never throws |

### Tier 1 — retention storage

| File | Ported from | Notes |
|---|---|---|
| `database/AyuDatabase.java` | `data/ayu_database.cpp`, `data/messages_storage.cpp` | own SQLite file `ayugram.db`, three tables (`deleted_messages`, `edited_messages`, `deleted_reactions`), stores raw `TLRPC.Message` blobs, no new dependencies |

### Tier 2 — features

| File | Ported from | Notes |
|---|---|---|
| `AyuGhost.java` | `ayu_worker.cpp` + desktop `MTP::` hooks | network-layer suppression of read receipts / online status / story reads |
| `filters/AyuFilter.java` | `features/filters/filters_utils.h` | filter model |
| `filters/AyuFilterController.java` | `features/filters/filters_controller.cpp`, `filters_cache_controller.cpp` | CRUD + compiled-regex cache + matching |
| `ui/AyuGramPreferencesActivity.java` | `ui/settings/*` | standalone settings screen, programmatic UI, every toggle persists immediately |

### Upstream patches (the only edits to Telegram's own files)

1. `MessagesStorage.markMessagesAsDeletedInternal` — one call to
   `AyuHooks.onMessagesDeleted` before rows are erased.
2. `MessagesStorage.replaceMessageIfExists` — reads the previous revision and calls
   `AyuHooks.onMessageEdited`.
3. `ConnectionsManager.sendRequestInternal` — drops ghost-mode-suppressed requests
   and answers them with a synthetic response.
4. `ApplicationLoader.onCreate` — `AyuConfig.load()` at startup.
5. `AndroidManifest.xml` — registers `AyuGramPreferencesActivity`.
6. `res/values/strings.xml` — `AppName` = PrimeGram.

---

## What is NOT done yet

- **Message history viewer** — the retention store captures deleted/edited messages,
  but there is no screen to browse them yet. `AyuDatabase.getDeletedMessages` /
  `getEditedMessages` are the data source.
- **Filter enforcement** — `AyuFilterController.matches()` works, but nothing calls
  it on the message-render path yet, so filters do not hide anything in the UI.
- **AyuForward** — `features/forward` (~2000 LOC) not started.
- **Message shot** — `features/message_shot` (~1050 LOC) not started; needs a
  message-to-bitmap renderer.
- **Translator** — `features/translator` (~750 LOC) not started.
- **Settings entry point** — the activity exists and is registered, but there is no
  in-app row that launches it yet.
- **Ghost mode: typing, upload progress, stories prompt** — the flags exist and are
  honoured where wired, but `sendUploadProgress` and `suggestGhostModeBeforeViewingStory`
  are not enforced anywhere yet.

---

## Build

This machine cannot build the APK (3.8 GB RAM vs `-Xmx8g` in `gradle.properties`).
Build on a machine with 16 GB+ RAM:

```bash
./gradlew TMessagesProj:assembleAfatDebug
```

Requires JDK 17, Android SDK platform 35/36 and NDK 27.2 (all present at
`/opt/android-sdk` if you build here, but RAM is the blocker).
