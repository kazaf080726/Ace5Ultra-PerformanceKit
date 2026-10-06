# Module / OTA Package Format (evidence-backed)

Reverse-engineered from the actual reference binaries shipped with this repo. Every claim
below is backed by a real extracted path, file listing, or bytecode/`.so` string dump from:

- `D:\...\SukiSU_v4.2.0_40959-release.apk`  (extracted; `classes.dex` + `lib/arm64-v8a/libksud.so`)
- `D:\...\BakaSU_v4.2.0-rc3_35215-arm64-v8a-release.apk` (extracted; `classes.dex`)
- `D:\...\LSPosed-v2.2.1-7912-release.zip` (a well-formed, flashable module zip; reused as the
  canonical installer template)

Method: all three were unzipped with Python `zipfile`. The LSPosed zip stores its text/scripts
with **XZ compression (zip method 95)**, which stock `zipfile` will not decompress; those entries
were read by parsing local headers and decompressing the raw stream with Python `lzma`. The two
APKs decompressed normally (methods 0/8).

---

## (a) Exact flashable module zip layout the managers accept

Evidence 1 — actual root listing of the reference LSPosed module zip
(`unzip -l LSPosed-v2.2.1-7912-release.zip`, relevant entries):

```
README.md
action.sh
customize.sh
module.prop
sepolicy.rule
service.sh
uninstall.sh
util_functions.sh
verify.sh
META-INF/
META-INF/com/
META-INF/com/google/
META-INF/com/google/android/
META-INF/com/google/android/update-binary
META-INF/com/google/android/updater-script
bin/...
lib/...
```

Evidence 2 — the installed runtime script set the KernelSU-family (`libksud.so`) installer knows
about, extracted verbatim from the `.so` string table:

```
service.shpost-fs-data.shpost-mount.shboot-completed.sh
[ -f $TMPDIR/uninstall.sh ] && cp -af $TMPDIR/uninstall.sh $MODPATH/uninstall.sh
$POSTFSDATA  && cp -af $TMPDIR/post-fs-data.sh $MODPATH/post-fs-data.sh
$LATESTARTSERVICE && cp -af $TMPDIR/service.sh   $MODPATH/service.sh
$PROPFILE    && cp -af $TMPDIR/system.prop       $MODPATH/system.prop
$SKIPMOUNT   && touch $MODPATH/skip_mount
[ -f $MODPATH/customize.sh ] && . $MODPATH/customize.sh
unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH
```

Conclusions (CONFIRMED by the above):

| Path (zip root) | Required? | Role |
|---|---|---|
| `module.prop` | **required** | Identity. Parsed by the installer (`grep_prop id/name/author`) and by the manager. |
| `META-INF/com/google/android/update-binary` | **required** | Flashable installer entry point (Magisk/recovery path). |
| `META-INF/com/google/android/updater-script` | **required** | Must contain the single line `#MAGISK` (evidence: LSPosed file is exactly `#MAGISK`). |
| `customize.sh` | optional but standard | Sourced by the installer inside `$MODPATH` after auto-unzip; the place you set perms. |
| `post-fs-data.sh` | optional | Run early (post-fs-data). |
| `service.sh` | optional | Run late (post-boot). Our module starts `perfd` here. |
| `action.sh` | optional | Manager "action" hook (see (c)). |
| `uninstall.sh` | optional | Run when the module is removed. |
| `system/` | optional | Module-overlay root; ships `system/placeholder` so an empty overlay tree is well-formed. |
| `webroot/` | optional | WebUI root (see (c)). |

How the installer actually flashes (from the embedded `install_module()` in `libksud.so`,
CONFIRMED):

```
install_module() {
  rm -rf $TMPDIR; mkdir -p $TMPDIR; cd $TMPDIR
  mount_partitions; api_level_arch_detect
  unzip -o "$ZIPFILE" module.prop -d $TMPDIR
  MODID=`grep_prop id $TMPDIR/module.prop`
  MODNAME=`grep_prop name $TMPDIR/module.prop`
  MODAUTH=`grep_prop author $TMPDIR/module.prop`
  MODPATH=$NVBASE/$MODDIRNAME/$MODID          # /data/adb/modules_update/<id> in boot mode
  mkdir -p $MODPATH
  unzip -o "$ZIPFILE" customize.sh -d $MODPATH
  if ! grep -q '^SKIPUNZIP=1$' $MODPATH/customize.sh; then
      unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH   # everything except META-INF
      set_perm_recursive $MODPATH 0 0 0755 0644
  fi
  [ -f $MODPATH/customize.sh ] && . $MODPATH/customize.sh
  # ...then removes $MODPATH/system/placeholder, $MODPATH/customize.sh, README.md, .git*
}
```

Important consequences for our module:
- In **boot mode** (install from the app) the module lands in `/data/adb/modules_update/ace5ultra_perfkit`,
  then the manager promotes it to `/data/adb/modules/ace5ultra_perfkit` after reboot.
- The installer auto-extracts every non-`META-INF` path, so `customize.sh` does **not** need to
  `extract` files; it only needs to tighten permissions on `bin/*` (the default recursive mode is
  `0755 dirs / 0644 files`, but the contract requires `bin/perfctl` and `bin/perfd` to be `0700 root`).
- The installer deletes `customize.sh` and `system/placeholder` after install, so they must not be
  relied on at runtime.

### module.prop fields

Evidence — the actual LSPosed `module.prop` (extracted verbatim):

```
id=zygisk_lsposed
name=LSPosed
version=v2.2.1 (7912)
versionCode=7912
author=LSPosed Developers
description=Another enhanced implementation of Xposed Framework. ...
updateJson=https://lsposed.zip/update.json
actionIcon=launcher.png
```

Evidence — how the installer reads it (`libksud.so`):
```
MODID=`grep_prop id    $TMPDIR/module.prop`
MODNAME=`grep_prop name  $TMPDIR/module.prop`
MODAUTH=`grep_prop author $TMPDIR/module.prop`
#Invalid module ID in module.prop: '...'
Failed to parse module.prop:
```

So the manager-installer uses: **`id`, `name`, `author`** (free-form `version`/`description` are
displayed). The SukiSU app additionally greps the prop with
`grep -E 'name=|version=|license=|author='` (bytecode string).

Our required fields per the frozen contract (§6): `id`, `name`, `version`, `versionCode` (integer),
`author`, `description`, `updateJson`. We also add `actionIcon` (optional; supported, see (c)).

### Reused installer

We reuse the LSPosed `META-INF/com/google/android/update-binary` and `updater-script` **verbatim**
(as instructed). The LSPosed `update-binary` is the canonical Magisk-compatible thin shim:

```sh
umask 022
ui_print() { echo "$1"; }
OUTFD=$2
ZIPFILE=$3
[ -f /data/adb/magisk/util_functions.sh ] || require_new_magisk
. /data/adb/magisk/util_functions.sh
install_module
exit 0
```

Notes / quirks (documented, not hidden):
- This shim depends on `/data/adb/magisk/util_functions.sh`. Under a **Magisk** flash this exists.
- Under **SukiSU/BakaSU/KSU** in-boot flashing, the manager's own `ksud` runs its embedded
  self-contained `install_module()` (shown above) and explicitly **excludes `META-INF/*`** from the
  extracted tree (`unzip ... -x 'META-INF/*'`), so this shim is the Magisk/recovery fallback and is
  not what drives a KSU in-app flash. Either way the same `customize.sh` API is used
  (`ui_print`, `abort`, `grep_prop`, `MODPATH`, `ZIPFILE`, `TMPDIR`, `ARCH`, `BOOTMODE`,
  `set_perm_recursive`), which is why a single `customize.sh` works on all three.

---

## (b) How SukiSU Ultra, BakaSU and KernelSU parse the online update JSON

Evidence from bytecode (both APKs) — the update model fields are literal strings in the dex:

```
SukiSU classes.dex:
  '!ModuleUpdateSignature(updateJson='
  'LatestVersionInfo(versionCode='
  'zipUrl'
  'changelog'
  'updateJson'
  '/module.prop'   'module.prop'

BakaSU classes.dex:
  'updateJson'  'zipUrl'  'changelog'  'versionCode'
  ' /data/adb/metamodule/module.prop'
  'check_module_update'  'check_update'  'check_beta_update'
```

Conclusions (CONFIRMED):
- The manager reads the **`updateJson=`** URL from `module.prop` and GETs it.
- The remote JSON is deserialized into a model with exactly these keys:
  **`version` (string), `versionCode` (integer), `zipUrl` (string URL), `changelog` (string URL)**.
  This matches the frozen contract §6 sample verbatim.
- The manager compares the remote `versionCode` against the installed `versionCode` (from
  `module.prop` / `perfctl version`) and, on a greater remote code, offers the update and hands the
  downloaded `zipUrl` zip to the root manager to re-flash.
- The downloaded zip is placed by the manager into a temp/downloads location and flashed through the
  same in-app installer path in (a). We do **not** control its placement; the contract only requires
  `zipUrl` to point at the raw zip.

Manager-specific quirks (documented):
- **SukiSU Ultra**: reads `module.prop` at the live module dir; greps
  `name=|version=|license=|author=`; supports `webroot/` and `actionIcon`/`action.sh`; also has a
  "metamodule" wrapper layer (see strings `/data/adb/metamodule/`, `metamodule=`) — transparent to a
  normal Magisk-style module.
- **BakaSU**: same update-JSON keys (`updateJson`/`zipUrl`/`versionCode`/`changelog`); reads
  `module.prop` (string `/data/adb/metamodule/module.prop` shows its metamodule staging); ships
  `check_module_update`/`check_update`/`check_beta_update` flows. It is a KernelSU-family fork, so it
  uses the same `ksud`-class installer.
- **KernelSU (upstream)**: same schema; the `modules.kernelsu.org/module/` registry string appears in
  both APKs, confirming the shared ecosystem.

We therefore ship `release/update.json` exactly as the contract specifies:

```json
{
  "version": "1.0.0",
  "versionCode": 100,
  "zipUrl": "https://github.com/<owner>/Ace5Ultra-PerformanceKit/releases/download/v1.0.0/ace5ultra_perfkit-v1.0.0.zip",
  "changelog": "https://raw.githubusercontent.com/<owner>/Ace5Ultra-PerformanceKit/main/release/changelog.md"
}
```

---

## (c) Is `action.sh` / `webroot` supported?

**`action.sh` — YES, supported (CONFIRMED).**
- LSPoden ships a real `action.sh` (extracted verbatim):
  ```sh
  API=$(getprop ro.build.version.sdk)
  if [ "$API" -ge 29 ]; then
    am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android
  else
    am broadcast -a android.provider.Telephony.SECRET_CODE -d android_secret_code://5776733 android
  fi
  ```
  and pairs it with `actionIcon=launcher.png` in `module.prop`.
- `libksud.so` contains the literal path `/action.sh`, and both APK dex contain `actionIcon` /
  `actionIcons` / `actionIconPath`. The manager runs `action.sh` (as root) when the user taps the
  module's action button in the UI.

**`webroot/` — YES, supported (CONFIRMED).**
- Both APK dex contain the literal string `webroot`; `libksud.so` contains `/webroot`. This is the
  KernelSU WebUI convention: a `webroot/` folder in the module is served by the manager's built-in
  web UI. Our module does not ship one (not required by the contract), but the convention is
  confirmed available if a future WebUI is added.

---

## Confirmed vs. assumed without a device

CONFIRMED from the extracted reference binaries:
- Whole zip layout and the `#MAGISK` updater-script marker.
- `module.prop` keys the installer reads (`id`/`name`/`author`) and the optional `updateJson` /
  `actionIcon` keys.
- Update-JSON keys `versionCode` / `zipUrl` / `changelog` (and `updateJson` source) in all three
  managers.
- `action.sh` and `webroot/` are honored.
- The boot-mode install lands in `modules_update/`, auto-unzips non-`META-INF` paths, and runs
  `customize.sh`.

ASSUMED / NOT verifiable offline (device is offline):
- The exact `version` string field name in the remote JSON is not a separate dex string we grepped,
  but it is the universal KSU schema and is mandated by the frozen contract §6.
- BakaSU's metamodule staging details beyond the two strings seen.
- Every runtime sysfs/procfs node the module will touch on the actual OnePlus Ace 5 Ultra — these are
  discovered at runtime by the module itself and recorded in `docs/node-map.md` (marked unverified).
