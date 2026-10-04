# Cut Ledger / План раскроя

Original Android utility for finite 1D stock cutting. Metric millimetres, kerf and end trim, original diagrams, local drafts/projects and private CSV sharing. No accounts or backend.

The numeric objective maximizes placed length, then placed count, then reduces used raw length and stock count. Complete search is reported only when fully exhausted; larger jobs are explicitly bounded plans. Integers represent micrometres, not floating point approximations.

Android 7+ / API24; target36; Russian and English. Support: jonkryl@gmail.com.

GitHub Actions compiles once, runs independent exhaustive-oracle regression tests and lint, then reuses the APK pair for actual API24/36 device journeys, real process restart, CSV content-provider verification and large text. Release requires a real own Yandex banner ID and protected signing secrets. Test IDs are debug-only. Play publication and RSYA application activation require separate external verification.
