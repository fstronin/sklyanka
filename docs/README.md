# Documentation

The documentation of Sklyanka is small on purpose: a normative specification, the architecture
decisions, and the original customer materials.

| Path | Content |
|---|---|
| [`spec/product.md`](spec/product.md) | what the product is, who it serves, goals, principles, composition, privacy, platform, success signals |
| [`spec/behavior.md`](spec/behavior.md) | data and invariants, schedule and intake, reminders, permissions and reliability onboarding, screens, profiles, transfer |
| [`spec/design-system.md`](spec/design-system.md) | the design system: tokens, colour, typography, geometry, components, accessibility |
| [`spec/design-screens.md`](spec/design-screens.md) | screen header and scrolling, motion, dark environment, layout localization, prohibitions |
| [`spec/quality.md`](spec/quality.md) | verification levels, build gates, quality requirements and their status |
| [`spec/architecture.md`](spec/architecture.md) | the stack, the module map and the data flow |
| [`spec/glossary.md`](spec/glossary.md) | domain terms, avoided synonyms, domain value codes |
| [`spec/privacy.md`](spec/privacy.md) | what the app does with information |
| [`adr/`](adr/) | architecture decision records, `0001` to `0018` |
| [`screenshots/`](screenshots/) | screen frames used by the README |

The customer's original specification and the screen mockups live in [`../design/`](../design/).
They are the untouched source and are written in Russian, the language the product was specified
in; the English specification above is the current description of the behaviour.

## Rules for this layer

1. A normative document states the rule that holds, not the history of how it was reached.
   A replaced rule is rewritten in place; the record of the decision lives in `adr/`.
2. One fact lives in one place. Other documents refer to it by its section number
   (`behavior.md` §5.1) instead of restating it.
3. Section numbers are stable: Kotlin KDoc cites them, so a section keeps its number when its
   text changes.
4. A requirement without a method of verification is not published in `quality.md`.
5. The interface is Russian; when a rule is about a specific label, the label is quoted in
   Russian with an English gloss: `«Принято»` ("Taken").
