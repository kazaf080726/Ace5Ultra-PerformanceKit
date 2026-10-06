# Signing the release APK

Android requires every release APK to be digitally signed. Debug builds are signed
automatically with the SDK debug key; release builds need a key you control.

## 1. Generate a keystore

Run this once (you will be prompted for a password and a name; keep the keystore and
password safe and **do not commit them** — both are git-ignored):

```sh
keytool -keystore release.keystore -alias perfkit -keyalg RSA -keysize 2048 -validity 10000
```

Place `release.keystore` at the repository root, or point `storeFile` to wherever you keep
it.

## 2. Provide the credentials

Create `keystore.properties` at the repository root (git-ignored):

```properties
storeFile=release.keystore
storePassword=your_store_password
keyAlias=perfkit
keyPassword=your_key_password
```

## 3. Wire it into the Gradle build

`app/build.gradle.kts` reads `keystore.properties` when present and uses it for the release
`signingConfig`; if the file is absent it falls back to the debug key so local builds never
break. Example wiring:

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(FileInputStream(f))
}

android {
    signingConfigs {
        create("release") {
            if (rootProject.file("keystore.properties").exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName(
            if (rootProject.file("keystore.properties").exists()) "release" else "debug")
        }
    }
}
```

## Notes

- The release tooling (`scripts/bump_release.py`) runs `assembleRelease`; make sure the
  keystore is in place before publishing, or the release APK will be debug-signed.
- For GitHub Actions, store the four values as repository secrets and decode the keystore
  in the workflow if you want CI-signed release APKs. The published module zip is
  unaffected by APK signing.
