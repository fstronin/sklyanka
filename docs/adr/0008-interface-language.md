# 0008 — The interface language belongs to the application

Status: Accepted

## Context

The plural form of a string is chosen by the locale of the resource configuration, not by the
language of the string. An application with Russian strings on a device configured as `en-US`
printed «15 минуты» and "27 September". Weekday and month names inside dates have the same problem.
Language must therefore be decided at one entry point, and time format is a separate question: it is
a personal setting, not a property of a language.

## Decision

1. Six languages are declared: `ru` (base and fallback), `en-US`, `es`, `de`, `zh-CN`, `kk`. The set
   is stated in three places: the manifest (`app/src/main/res/xml/locales_config.xml`,
   `android:localeConfig`), the build (`ProjectDefaults.INTERFACE_LOCALES` →
   `resourceConfigurations`) and the code (`InterfaceLocale.SUPPORTED`). A guard test compares the
   three.
2. The application chooses the language, not the phone: the application context and every activity
   are wrapped in `attachBaseContext` with a locale from `InterfaceLocale.resolve(deviceLocales)`.
   The first device language with a declared relative wins, matched by language and not by country
   (`en-GB` finds `en-US`, `zh-TW` finds `zh-CN`). With no declared language the fallback is `ru`
   entirely, so Russian declensions and formats arrive together with Russian strings.
3. The base resource set is Russian (`values/`), there is no `values-ru`; translations are
   `values-{de,en-rUS,es,kk,zh-rCN}` in `app` and `core-ui`. A guard test pins this.
4. The 12- or 24-hour preference is read from the system setting (`DateFormat.is24HourFormat`).
5. The storage layer and the transfer file format values with `Locale.ROOT`: a machine value does
   not depend on the phone language in either direction (ADR-0013).
6. There is no in-app language switch. `LocaleManager` (API 33+) is not used because of `minSdk 26`.

## Consequences

- All five activities pass through `withInterfaceLocale()`; notification contexts, the PDF report and
  the default profile name receive the same wrapping. An activity added outside this point is caught
  by an acceptance step, not by review.
- Translation completeness, plural forms, placeholders and the absence of Russian text in other
  translations are checked by a guard test.
- The localization matrix of six languages is part of acceptance for any screen change.
- Screen runs use a device locale other than Russian, so the run is not blind to this class of
  defect.

## Alternatives

Keeping the interface Russian only was rejected because the app is published to stores with a
multilingual audience, and a fixed `ru` locale would still have to be applied deliberately rather
than inherited from the device.
