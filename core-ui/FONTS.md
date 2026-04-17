# Bundled Fonts

The typography scale in `Typography.kt` is **committed** — sizes, weights, line heights, and letter spacing are final. The only thing pending is the **actual typeface files**.

Until bundled, `DisplayFontFamily` resolves to `FontFamily.Serif` (Noto Serif on Android) and `BodyFontFamily` to `FontFamily.Default`. That's a real upgrade from the previous all-Roboto state, but not the final pairing.

## Target pairing

| Role | Typeface | License | Why this font |
|------|----------|---------|---------------|
| Display / spell names / section titles | **EB Garamond** | SIL OFL 1.1 | Manuscript serif, warm, readable at 14sp. Authority without costume. |
| Body / UI / labels | **Atkinson Hyperlegible** | SIL OFL 1.1 | Designed by the Braille Institute for maximum character distinction in poor lighting — engineered for exactly the at-table context. |

Neither appears on the reflex-reject list in `.impeccable.md`. Pairing is serif + humanist sans, genuine contrast.

## Drop-in procedure

1. **Download the font files** (OFL, free to bundle, offline):
   - EB Garamond: https://fonts.google.com/specimen/EB+Garamond → download family zip → extract these `.ttf` files:
     - `EBGaramond-Regular.ttf`
     - `EBGaramond-Medium.ttf`
     - `EBGaramond-SemiBold.ttf`
     - `EBGaramond-Italic.ttf` (optional — for emphasized rules text if you want it later)
   - Atkinson Hyperlegible: https://fonts.google.com/specimen/Atkinson+Hyperlegible → download family zip → extract:
     - `AtkinsonHyperlegible-Regular.ttf`
     - `AtkinsonHyperlegible-Bold.ttf`
     - `AtkinsonHyperlegible-Italic.ttf` (optional)

2. **Rename to Android resource conventions** (lowercase, underscores) and place in `core-ui/src/main/res/font/`:

   ```
   core-ui/src/main/res/font/
     eb_garamond_regular.ttf
     eb_garamond_medium.ttf
     eb_garamond_semibold.ttf
     atkinson_hyperlegible_regular.ttf
     atkinson_hyperlegible_bold.ttf
   ```

3. **Create two font-family XML resources** (tells Compose how to resolve weights):

   `core-ui/src/main/res/font/eb_garamond.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <font-family xmlns:android="http://schemas.android.com/apk/res/android">
       <font android:fontStyle="normal" android:fontWeight="400" android:font="@font/eb_garamond_regular" />
       <font android:fontStyle="normal" android:fontWeight="500" android:font="@font/eb_garamond_medium" />
       <font android:fontStyle="normal" android:fontWeight="600" android:font="@font/eb_garamond_semibold" />
   </font-family>
   ```

   `core-ui/src/main/res/font/atkinson_hyperlegible.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <font-family xmlns:android="http://schemas.android.com/apk/res/android">
       <font android:fontStyle="normal" android:fontWeight="400" android:font="@font/atkinson_hyperlegible_regular" />
       <font android:fontStyle="normal" android:fontWeight="500" android:font="@font/atkinson_hyperlegible_bold" />
       <font android:fontStyle="normal" android:fontWeight="700" android:font="@font/atkinson_hyperlegible_bold" />
   </font-family>
   ```

4. **Swap the two aliases in `Typography.kt`** — replace:
   ```kotlin
   private val DisplayFontFamily: FontFamily = FontFamily.Serif
   private val BodyFontFamily: FontFamily = FontFamily.Default
   ```
   with:
   ```kotlin
   private val DisplayFontFamily: FontFamily = FontFamily(Font(R.font.eb_garamond))
   private val BodyFontFamily: FontFamily = FontFamily(Font(R.font.atkinson_hyperlegible))
   ```
   and add imports:
   ```kotlin
   import androidx.compose.ui.text.font.Font
   import com.spellapp.core.ui.R
   ```

5. **Attribute the fonts** (offline app, OFL requires attribution somewhere reachable by the user — a future About/Settings screen is fine; OFL licenses can be shipped in assets).

Licensing: both OFL 1.1. Free to embed in the APK, free to redistribute, no runtime network fetch — satisfies the project's strict-offline constraint.

## Skipping Google Fonts / Downloadable Fonts

Do **not** use Compose's `GoogleFont` / `DownloadableFont` APIs — they fetch at runtime and would violate the offline constraint enforced by `checkNoInternetPermission` + the no-network-dependency gradle gate. Bundled `res/font/` files only.
