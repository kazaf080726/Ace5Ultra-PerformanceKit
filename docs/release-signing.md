# Release signing

The release build is signed with a JKS keystore kept **outside the committed
source tree**, in `release-signing/` (gitignored). Credentials live in
`app/signing.properties` (also gitignored).

## Files

| Path | Purpose |
|---|---|
| `release-signing/perfkit-release.jks` | RSA 2048 keystore, alias `perfkit`, 10000 days |
| `app/signing.properties` | storeFile / storePassword / keyAlias / keyPassword |

`app/build.gradle.kts` reads `app/signing.properties` at build time. If the
file is absent (e.g. a fresh clone), release builds fall back to unsigned so
CI can still assemble; to produce a signed release APK locally, restore both
files.

## Recreate the keystore

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore release-signing/perfkit-release.jks -alias perfkit `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass <storepass> -keypass <keypass> `
  -dname "CN=Ace5Ultra PerfKit, O=<owner>, L=Macau, C=MO"
```

Then write `app/signing.properties`:

```
storeFile=../release-signing/perfkit-release.jks
storePassword=<storepass>
keyAlias=perfkit
keyPassword=<keypass>
```

## Build

```powershell
$env:JAVA_HOME = "C:\Users\Administrator\android-dev\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "C:\Users\Administrator\android-dev\sdk"
.\gradlew.bat assembleRelease
```
