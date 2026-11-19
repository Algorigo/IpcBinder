# IpcBinder (RxIpcBinder)

다른 프로세스나 다른 앱의 `Service`에 있는 RxJava `Observable`을 Binder IPC로 구독할 수 있게 해 주는 Android 라이브러리입니다. 클라이언트는 원격 스트림을 로컬 `Observable`처럼 `subscribe` / `dispose`하고, 서비스 쪽에서는 요청 타입별로 `Observable`만 만들어 돌려주면 됩니다.

- 배포 아티팩트: `com.algorigo.rx:rxipcbinder` (버전은 루트 `gradle.properties`의 `versionStr`, 현재 `0.2.0`)
- 언어/스택: Kotlin, RxJava3, AIDL
- 최소 Android 버전: 5.0 (API 21)
- 루트 프로젝트 이름: `AlgorigoBridge`

## 동작 방식

```
[클라이언트 앱]                                   [서비스 앱 (별도 프로세스)]
RxIpcBinder.bind(...)  ── bindService ──────────▶  RxService.onBind
  .flatMap { getObservable(type, params) } ─────▶  getObservable(type, params) → Observable
  subscribe ─────────────────────────────────────▶ subscribe → Schedulers.io()에서 구독
  onNext/onError/onComplete ◀── callbackMessage ── (클래스 이름, 바이트 배열)
```

- 값은 `ByteArrayObject`를 상속한 객체로 주고받습니다. 전송할 때는 `toByteArray()`로 직렬화하고, 받는 쪽에서는 **같은 이름의 클래스**를 `(ByteArray)` 생성자로 다시 만듭니다.
- 그래서 전송 타입과 타입 ID 같은 "계약"은 양쪽 앱이 함께 쓰는 모듈에 둡니다. 샘플에서는 `bridge` 모듈이 이 역할을 합니다.
- 바인딩할 때 버전을 비교합니다. 서비스 버전이 더 높으면 `NeedToUpdateClient`, 낮으면 `NeedToUpdateService` 에러가 납니다.

## 빌드

### 요구 사항

- JDK 11
- Android SDK 32 (compileSdk / targetSdk)

### Gradle 속성

다음 속성을 `~/.gradle/gradle.properties`에 설정하세요. 저장소에 커밋하지 마세요.

```properties
# 배포 저장소. master 브랜치에서는 NEXUS_REPOSITORY_URL, 그 외 브랜치에서는 NEXUS_SNAPSHOT_REPOSITORY_URL이 필요합니다.
# 없으면 assembleDebug 같은 일반 빌드도 Gradle 설정 단계에서 실패합니다.
NEXUS_REPOSITORY_URL=...
NEXUS_SNAPSHOT_REPOSITORY_URL=...

# publish 할 때만 필요
nexusUsername=...
nexusPassword=...
# GPG 서명용 signing.* 속성 (Gradle signing 플러그인)
```

### 명령어

```bash
./gradlew build                          # CI와 동일
./gradlew :app:installDebug              # 샘플 서비스 앱 설치
./gradlew :binderapp:installDebug        # 샘플 클라이언트 앱 설치
./gradlew :rxipcbinder:lintDebug
./gradlew :rxipcbinder:testDebugUnitTest
```

테스트는 템플릿 예제뿐입니다. 실제 동작은 두 샘플 앱을 한 기기에 설치하고 `binderApp`의 **Bind** / **Start** / **Start 2** 버튼으로 확인합니다.

## 의존성 추가

```groovy
repositories {
    maven { url NEXUS_REPOSITORY_URL }   // 릴리스 버전을 배포하는 저장소
}

dependencies {
    implementation 'com.algorigo.rx:rxipcbinder:0.2.0'

    // 배포된 POM에 의존성이 들어 있지 않으므로 RxJava3를 직접 추가해야 합니다.
    implementation 'io.reactivex.rxjava3:rxkotlin:3.0.1'
    implementation 'io.reactivex.rxjava3:rxandroid:3.0.0'
}
```

> 아래 예제의 버전 확인 API(`bind(..., binderVersion)`, `getServiceVersion()`)는 `develop`에만 있고, 릴리스된 `0.2.0`에는 없습니다. AIDL 인터페이스도 바뀌었으므로, 0.2.0으로 빌드한 앱과 `develop`으로 빌드한 앱은 서로 통신할 수 없습니다.

## 사용 방법

샘플 모듈(`bridge`, `app`, `binderapp`)을 줄인 예제입니다.

### 1. 공유 모듈: 전송 타입

```kotlin
class StringObject() : ByteArrayObject() {
    private lateinit var string: String

    constructor(string: String) : this() { this.string = string }
    constructor(byteArray: ByteArray) : this() {           // 받는 쪽에서 리플렉션으로 호출됩니다
        string = byteArray.toString(Charsets.UTF_8)
    }

    override fun toByteArray() = string.toByteArray(Charsets.UTF_8)
    override fun toString() = string
}
```

### 2. 서비스 앱

```kotlin
class BridgeService : RxService() {
    override fun getObservable(type: Int, values: ByteArray): Observable<ByteArrayObject> =
        when (type) {
            0 -> Observable.interval(1, TimeUnit.SECONDS).map<ByteArrayObject> { StringObject(it.toString()) }
            else -> Observable.error(IllegalArgumentException())
        }.subscribeOn(Schedulers.io())   // 넣지 않으면 Binder 스레드에서 구독됩니다

    override fun getServiceVersion() = 1
}
```

```xml
<service
    android:name=".BridgeService"
    android:exported="true"
    android:process=":bridge">
    <intent-filter>
        <action android:name="com.algorigo.bridge.BridgeService" />
    </intent-filter>
</service>
```

### 3. 클라이언트 앱

```xml
<!-- Android 11(API 30) 이상에서 서비스 앱 패키지를 찾으려면 필요합니다 -->
<queries>
    <package android:name="com.algorigo.bridge" />
</queries>
```

```kotlin
disposable = RxIpcBinder.bind(context, "com.algorigo.bridge", "com.algorigo.bridge.BridgeService", 1)
    .flatMap { it.getObservable(0, byteArrayOf()) }
    .map { it as StringObject }
    .observeOn(AndroidSchedulers.mainThread())   // 값은 Binder 스레드에서 들어옵니다
    .subscribe({ Log.i(TAG, "$it") }, { Log.e(TAG, "", it) })

disposable.dispose()   // 원격 구독을 해제하고, 1초 뒤 unbind 합니다
```

`bind`의 세 번째 인자는 클래스 이름이 아니라 **intent action**입니다. 서비스의 `<intent-filter>` action과 같아야 합니다.

## 프로젝트 구조

```
rxipcbinder/   배포 라이브러리: AIDL(IRxService, IRxServiceCallback), RxIpcBinder, RxService, ByteArrayObject
bridge/        샘플 계약 모듈: BridgeBinder, BridgeObservableType, IntervalObservable, StringObject, CommVersion
app/           샘플 서비스 앱 (com.algorigo.bridge): BridgeService를 ":bridge" 프로세스에서 실행
binderapp/     샘플 클라이언트 앱 (com.algorigo.binderapp)
```

자세한 프로토콜과 주의 사항은 [CLAUDE.md](CLAUDE.md)를 참고하세요.

## 알아둘 점

- 전송 타입은 양쪽 APK에 **같은 패키지·클래스 이름**으로 있어야 하고, public `(ByteArray)` 생성자가 있어야 합니다. R8로 난독화하면 이 클래스들을 keep 해야 합니다. 라이브러리의 `consumer-rules.pro`는 비어 있습니다.
- 서비스에서 난 예외는 `RxIpcBinder.IpcBinderException("<예외 클래스> : <스택 트레이스>")`로 전달됩니다. 원래 예외 타입은 유지되지 않습니다.
- `Observable`만 지원합니다.
- 샘플 `BridgeService`는 권한 없이 `exported="true"`로 열려 있습니다.

## 릴리즈

git-flow 브랜치 전략(`master`, `develop`, `feature/*`, `release/vX.Y.Z`)을 사용합니다.

1. `release/vX.Y.Z` 브랜치에서 `gradle.properties`의 `versionStr`을 올립니다. 커밋 메시지 예: `version name 수정`
2. `master`에 머지하면 CircleCI가 `./gradlew build` 후 `./gradlew publish`를 실행합니다.
   - `master` 브랜치에서는 `versionStr` 그대로 배포됩니다.
   - 그 외 브랜치에서는 `<versionStr>-<사용자명>-SNAPSHOT`으로 배포됩니다.
3. 로컬에서 배포할 때는 `publish`가 AAR을 빌드하지 않으므로 `./gradlew :rxipcbinder:assembleRelease`를 먼저 실행합니다.
