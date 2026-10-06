# Credits & Third-Party Notices

Ace5Ultra-PerformanceKit is built on the work of many open-source projects. We are grateful
to their authors and ask that you preserve these notices. Unless stated otherwise, the
project's own code is licensed under **GPL-3.0-or-later** (see [LICENSE](LICENSE)).

## Bundled / directly reused

| Project | Copyright / Author | License | Use in this project |
|---|---|---|---|
| Magisk module installer template (`META-INF/com/google/android/update-binary`, `updater-script`) | Copyright (c) 2016–present, John Wu (topjohnwu) and contributors | GPL-3.0 | The standard, manager-compatible module installer harness used to flash this module |
| KernelSU | Copyright (c) 2022–present, KernelSU contributors (tiann et al.) | GPL-3.0 | Root manager integration and module ABI reference |
| SukiSU Ultra | SukiSU contributors (KernelSU fork) | GPL-3.0 | Manager compatibility reference |
| BakaSU | BakaSU contributors (KernelSU fork) | GPL-3.0 | Manager compatibility reference |

## Libraries used by the app (resolved via Gradle; not redistributed as source)

| Project | Copyright / Author | License |
|---|---|---|
| libsu | Copyright (c) 2021, John Wu (topjohnwu) | Apache-2.0 |
| Jetpack Compose / AndroidX / Material Components | Copyright (c) Google LLC and contributors | Apache-2.0 |
| Kotlin stdlib & coroutines | Copyright (c) JetBrains s.r.o. and contributors | Apache-2.0 |
| kotlinx.serialization | Copyright (c) JetBrains s.r.o. and contributors | Apache-2.0 |

## Design references

| Project | Author | License | How it is used |
|---|---|---|---|
| [Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) | QWEA0 | MIT | Reference for the liquid-glass visual language and blur technique; ideas adapted into original Compose code |
| [miuix](https://github.com/compose-miuix-ui/miuix) | compose-miuix-ui contributors | Apache-2.0 | Reference for frosted/glass Compose components and interaction styling |
| [LCardView](https://github.com/linwg1988/LCardView) | linwg1988 | No license declared | **Visual reference only.** The repository does not ship a license file, so no source code is copied; only the general card aesthetic informed the original implementation. Courtesy credit to the author. |
| Android predictive-back gesture guides | Google LLC | Apache-2.0 documentation | Reference for `android:enableOnBackInvokedCallback` + `PredictiveBackHandler` back-preview: <https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture> and <https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back> |

### MIT License text — Liquid-Glass-Android (QWEA0)

Permission is hereby granted, free of charge, to any person obtaining a copy of the
Liquid-Glass-Android software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights to use, copy,
modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to the following
conditions: the above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT
WARRANTY OF ANY KIND. See the upstream repository for the full MIT license text.

### Apache-2.0 projects

libsu, miuix, Jetpack Compose/AndroidX, Kotlin and kotlinx are licensed under the Apache
License, Version 2.0. You may obtain a copy at <https://www.apache.org/licenses/LICENSE-2.0>.

### GPL-3.0 projects

The Magisk module installer template, KernelSU, SukiSU Ultra and BakaSU are licensed under
the GNU General Public License v3.0, available at <https://www.gnu.org/licenses/gpl-3.0.html>.

---

If you are an author listed above and believe any attribution is inaccurate or incomplete,
please open an issue and it will be corrected promptly.
