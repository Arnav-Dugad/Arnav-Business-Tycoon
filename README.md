# Arnav Business Tycoon

*Build the empire. Pass on the crown.*

An original finance idle tycoon for Android by **Arnav Games**. Grow a corner kiosk into a multi-generational dynasty through businesses, markets, real estate, luxury collections, rivals and a living world economy.

> **Status: in development.** See [docs/PROGRESS.md](docs/PROGRESS.md) and [docs/ROADMAP.md](docs/ROADMAP.md).
>
> **Store note:** every in-game purchase is a **simulated demo purchase**. No real money is ever charged and no payment details are ever requested.

## Documentation
| Doc | Contents |
|---|---|
| [GDD](docs/GDD.md) | Game design |
| [ECONOMY](docs/ECONOMY.md) | Formulas, pacing, balance targets, simulator |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | Modules, data model, saves, sync, Firebase |
| [CONTENT](docs/CONTENT.md) | Every content category with counts |
| [ART_PIPELINE](docs/ART_PIPELINE.md) | Photo sourcing and licensing |
| [TEST_PLAN](docs/TEST_PLAN.md) | Tests and release gates |

## Building from source
Requirements: JDK 21+ (Android Studio's bundled JBR works), Android SDK with platform 37.

```bash
./gradlew :core:test          # rules engine tests
./gradlew :app:assembleDebug  # debug APK
```

Without `app/google-services.json` the app builds as the **offline edition** (all gameplay works; cloud features are hidden).

Install steps for friends, screenshots and the feature list will be added with the first release.

## License and credits
Code © Arnav Games. Fonts: Inter and Fraunces (SIL Open Font License 1.1, see [licenses/](licenses/)). Photo and audio credits are listed in-app on the Credits screen.
