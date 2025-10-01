# Google Play submission checklist

This release targets Android 35 and includes a privacy policy URL.

## Store listing
- Privacy Policy URL: https://saeargeir.github.io/SkanniApp/privacy-policy.html
- Category/graphics as usual

## App content
- Privacy policy: set URL above
- Data safety: camera permission used for scanning; data processed on-device; optional CSV/Excel sharing; optional cloud folder via Storage Access Framework; no analytics or third-party SDKs.

## Artifacts
- Use the signed `app-release.aab` from GitHub Releases for the tagged version.
- Optional: upload `native-debug-symbols.zip` to Symbols in Play Console (attached to the release assets).

## Notes
- Camera is declared as an optional feature; gallery import is supported.
