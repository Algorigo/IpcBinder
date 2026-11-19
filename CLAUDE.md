# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

RxIpcBinder is an Android library (Kotlin, RxJava3, AIDL) that lets a client subscribe to RxJava `Observable`s living in a `Service` in another process or another app, over Binder IPC. Only the `rxipcbinder` module is published, as `com.algorigo.rx:rxipcbinder` via `maven-publish` + `signing` to the Nexus repository named by Gradle properties. Its version is `versionStr` in the root `gradle.properties`. The other three modules are samples. Root project name is `AlgorigoBridge`.

## Build & test

```bash
./gradlew build                                   # what CI runs on every branch
./gradlew :app:installDebug                       # sample service host (com.algorigo.bridge)
./gradlew :binderapp:installDebug                 # sample client (com.algorigo.binderapp)
./gradlew :rxipcbinder:lintDebug
./gradlew :rxipcbinder:testDebugUnitTest
./gradlew :rxipcbinder:testDebugUnitTest --tests "com.algorigo.rxipcbinder.ExampleUnitTest"
./gradlew :rxipcbinder:assembleRelease            # must run before publish (see below)
./gradlew :rxipcbinder:publish
```

Toolchain: AGP 7.0.4, Gradle 7.0.2, Kotlin 1.6.10, Java/JVM target 11, compileSdk/targetSdk 32, minSdk 21. Only template example tests exist. In `app` and `binderapp` they are oddly named `Example*StringObject.kt`. To check real behavior, install both sample apps on one device and use the binderapp buttons.

**Required Gradle properties** (in `~/.gradle/gradle.properties`, not in the repo; CI downloads that file from `$GRADLE_PROPERTY_PATH`):
- `NEXUS_SNAPSHOT_REPOSITORY_URL` on any branch except `master`, `NEXUS_REPOSITORY_URL` on `master`. The `publishing` block reads it at configuration time, so even `assembleDebug` fails without it.
- `nexusUsername`, `nexusPassword` (read with `findProperty`, so they are only needed for `publish`).
- GPG signing for `publish` uses the Gradle signing plugin's `signing.*` properties. CI writes the keyring to `rxipcbinder/secret-keys.gpg` (`$SECRET_KEY_RING_PATH`). That path is **not** in `.gitignore`, so never commit it.

**Publishing quirks** (`rxipcbinder/build.gradle`):
- `version` is `versionStr` only when `git rev-parse --abbrev-ref HEAD` is exactly `master`. Otherwise it is `<versionStr>-<user.name>-SNAPSHOT`.
- The publication attaches `build/outputs/aar/rxipcbinder-release.aar` by file path instead of `from components.release`. So `publish` does not build the AAR, and the generated POM has **no dependencies**. Consumers must declare RxJava3 themselves.
- CircleCI runs `./gradlew publish` on `master` and `release/*`, reusing the workspace from the `build` job.

## Modules

- `rxipcbinder` (published): the AIDL interfaces, `RxIpcBinder` (client), `RxService` (abstract server), and `ByteArrayObject` (payload base class).
- `bridge`: sample *contract* module. Both apps compile it. It holds `BridgeBinder` (typed client facade), `BridgeObservableType` (type ids), `IntervalObservable` (server-side stream plus its param encoding: an 8-byte big-endian period followed by 1 byte of `TimeUnit` ordinal), `StringObject` (payload), and `CommVersion`.
- `app`: sample host. `BridgeService : RxService` runs in the `:bridge` process. It is exported with an `<intent-filter>` action `com.algorigo.bridge.BridgeService` and no permission. `BridgeApplication.onCreate` starts it, and it goes foreground on O+.
- `binderapp`: sample client with Bind / Start / Start 2 buttons. It declares `<queries><package android:name="com.algorigo.bridge"/></queries>`. Any client targeting API 30+ needs the same to see the host package.

## Architecture

**Wire protocol** (`rxipcbinder/src/main/aidl`). `IRxService` has `setCallback`, `checkVersion`, `createRxObject`, `releaseRxObject`, `subscribe`, and `dispose`. The service pushes events back through `IRxServiceCallback.callbackMessage(subscribeId, message, className, bytes)`, with `message` taken from `RxMessageType` (1 next, 2 complete, 3 error). Three id levels are involved:
- `objectId` is the client `RxIpcBinder.hashCode()`. It is sent as the `RxService.OBJECT_ID` bind-intent extra and as the first argument of every call. All server state is keyed by it.
- `observableId` is a random int returned by `createRxObject(type, params)`.
- `subscribeId` is a random int returned by `subscribe`. The client routes callbacks by it to a `PublishSubject` in `subjectMap`.

**Client** (`RxIpcBinder`):
- `RxIpcBinder.bind(context, packageName, action, binderVersion)` binds with `Intent(action).setPackage(packageName)`. The third argument is an intent **action** (the sample just reuses the class name), so the service must declare a matching `<intent-filter>`.
- After `setCallback`, `bind` runs `checkVersion`, which returns `serviceVersion - binderVersion`. A result > 0 throws `NeedToUpdateClient`, and < 0 throws `NeedToUpdateService`.
- The emitted `RxIpcBinder` is usable only while the bind subscription lives. Disposing it disposes every remote subscription, errors live streams with `IpcDisconnectedException`, and unbinds 1 s later. If the service process dies, the bind stream errors with `IpcDisconnectedException`. The intended shape is `bind(...).flatMap { it.getObservable(type, params) }`.
- `getObservable(type, params)` calls `createRxObject` immediately, not at subscribe time. Each subscription to the returned `IpcObservable` makes its own remote `subscribe` call. `finalize()` calls `releaseRxObject`.

**Server** (`RxService`): implement `getObservable(type, values): Observable<ByteArrayObject>` and `getServiceVersion()`. `onBind` returns the real binder only if the intent carries `OBJECT_ID`, and an empty `Binder` otherwise. Each remote subscription is `observeOn(Schedulers.io())` and delivers every event as a synchronous Binder call into the client.

**Payloads** (`ByteArrayObject`): an item crosses the wire as (class name, `toByteArray()`). The client rebuilds it with `Class.forName(name).getConstructor(ByteArray::class.java).newInstance(bytes)`. So every emitted type must:
- subclass `ByteArrayObject` and have a public `(ByteArray)` constructor;
- exist under the **same fully-qualified name in both APKs**, which is why the contract lives in a shared module;
- not be renamed by R8 in either app. `consumer-rules.pro` is empty, and the samples have minify off.

Server errors arrive as `IpcBinderException("<exception class> : <stack trace>")`, so the original exception type is lost. Only `Observable` is supported, not Single, Completable, or Flowable.

**Adding a remote stream** (sample layout):
1. Add a type id in `BridgeObservableType`.
2. Add the param encoding and a `ByteArrayObject` result type in `bridge`.
3. Add a branch in `BridgeService.getObservable`.
4. Add a typed method in `BridgeBinder`.
5. Bump `CommVersion.VERSION` when the contract changes incompatibly.

## Threading and gotchas

- Client `onNext`/`onError` run on **Binder threads**. `observeOn(AndroidSchedulers.mainThread())` before touching UI, as binderapp does.
- `getObservable` and `IpcObservable.subscribeActual` are blocking Binder calls on the calling thread.
- `RxService.subscribe` subscribes the server observable on the incoming Binder thread. Add `subscribeOn` in `getObservable`, as `BridgeService` does with `Schedulers.io()`.
- `RxService`'s maps are plain `mutableMapOf`s touched from the Binder thread pool without synchronization.
- `releaseRxObject` does `observableMap.remove(observableId)` on a map keyed by `objectId`, so it is effectively a no-op. Created observables live until `onUnbind`/`onDestroy`.
- `onUnbind` cleans up only the `objectId` in its intent. Android calls `onBind`/`onUnbind` once per Intent filter (extras are ignored), not once per client. Real per-client teardown is the explicit `dispose` calls from `bind()`'s `doFinally`.
- If the service is gone when an `IpcObservable` is subscribed, `subscribeActual` returns without calling `onSubscribe`, and the subscriber hangs silently.
- **AIDL transaction codes follow declaration order.** `checkVersion` was inserted in the middle of `IRxService` after the 0.2.0 release (that commit is on `develop`, not `master`, and `versionStr` is still `0.2.0`). So 0.2.0 builds and `develop` builds are not wire-compatible. Append new AIDL methods at the end, and bump `versionStr` before the next release.

## Conventions

- Git-flow: `master`, `develop`, `feature/*`, `release/vX.Y.Z`. A release bumps `versionStr` in `gradle.properties` with the commit message `version name 수정`. Commit messages are mostly Korean.
- Every module reads `versionName` from the shared `versionStr`.
