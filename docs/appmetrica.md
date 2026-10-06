# AppMetrica

Cut Ledger uses SDK 8.5.1 in release builds with its own client SDK key. Debug builds have an empty key and skip activation. The Application class records automatic installation/session analytics and treats an existing installation's first activation as an update. No calculation content or custom events are sent.

Location, advertising identifier tracking, Java/native crashes, ANR and automatic purchase reporting are disabled. Advertising consent remains separate. The embedded bilingual policy in privacy-pages.yml describes this behavior and deploys to the existing public privacy URL.

Before a Google Play update, review Data Safety for AppMetrica technical identifiers and installation/session app activity for Analytics purposes, including IP-derived approximate location where required by Yandex's disclosure. Crash diagnostics must not be claimed as enabled. The SDK client key is a public application identifier; no Post API key or signing credential is included.

Verification uses the two private android-conveyor-ci runners. An unsigned release bundle proves compilation and manifest integration; it is not a signed store release or evidence of live analytics traffic.
