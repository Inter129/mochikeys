# 개발 문서

Mochi Keys를 직접 빌드하거나 고치려는 사람을 위한 문서입니다. 사용 방법은 [README](../README.md)를 보세요.

## 빌드

필요한 것: JDK 21, Android SDK(플랫폼 36).

```bash
./gradlew :core:test :app:assembleDebug
```

기기나 에뮬레이터가 연결돼 있으면 빌드·설치·키보드 선택·테스트 화면 실행까지 한 번에 합니다.

```bash
./scripts/ime-dev.sh
```

- `gradle.properties`의 `org.gradle.java.installations.paths`는 JDK 21 위치입니다. 환경에 맞게 고치세요.
- SDK 위치는 `local.properties`의 `sdk.dir`로 지정합니다(저장소에는 포함되지 않습니다).
- 에뮬레이터에서 계측 테스트(`:app:connectedDebugAndroidTest`)를 돌리면 끝날 때 앱이 지워집니다. 다시 `ime-dev.sh`를 실행하세요.

## 구조

```
core/   순수 Kotlin (JVM 단위 테스트)
  layout/   자판 정의, 키 배치 계산, 터치 영역 판정
  engine/   두벌식 한글 오토마타
  sound/    클릭음 합성
app/    안드로이드 앱
  ime/      InputMethodService, 언어별 입력 처리
  view/     키보드 그리기와 터치, 이모지 패널·검색, 아이콘
  mozc/     Mozc 세션 래퍼
  feedback/ 클릭음 재생
  settings/ 설정 화면(Material 3)
scripts/      개발용 스크립트
third_party/  포함한 외부 구성요소의 버전과 라이선스
docs/         배너, 아이콘, 스크린샷, 클릭음 미리 듣기
```

## 일본어 엔진(Mozc)

한자 변환은 [Mozc](https://github.com/google/mozc)를 JNI로 붙여 씁니다. 저장소에 빌드된 파일이 들어 있어 따로 준비할 것은 없습니다.

- `app/src/main/jniLibs/<CPU>/libmozc.so`: Mozc 공식 CI의 Android 빌드 산출물
- `app/src/main/assets/mozc.data`: 같은 커밋에서 `//data_manager/oss:mozc_dataset_for_oss`를 빌드한 사전

커밋과 파일 해시는 [`third_party/mozc/VERSION`](../third_party/mozc/VERSION)에 있습니다. 라이브러리와 사전은 반드시 같은 커밋이어야 합니다. 라이브러리는 네 가지 CPU(arm64-v8a, armeabi-v7a, x86_64, x86)용이 모두 들어 있고, 사전은 공용입니다. 릴리스는 CPU별 APK로 나눠 빌드합니다. 실제 동작을 확인한 것은 arm64-v8a뿐입니다.

## 릴리스

`v`로 시작하는 태그를 푸시하면 [`.github/workflows/release.yml`](../.github/workflows/release.yml)이 테스트를 돌리고 서명된 APK를 GitHub Release에 올립니다. Obtainium은 이 Release를 보고 설치·업데이트합니다.

1. `app/build.gradle.kts`의 `versionCode`를 1 올리고 `versionName`을 바꿉니다. `versionCode`가 그대로면 업데이트로 인식되지 않습니다.
2. 커밋하고 태그를 답니다.

```bash
git tag v0.1.0
```

```bash
git push origin main --tags
```

**서명 키**

- 모든 릴리스는 같은 키로 서명해야 합니다. 키가 바뀌면 기존 사용자는 업데이트를 받지 못하고 앱을 지웠다가 다시 깔아야 합니다. 키 저장소 파일과 비밀번호는 잃어버리지 않게 따로 보관하세요.
- 키는 저장소에 넣지 않습니다. 저장소 Secrets에 `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`를 등록합니다.
- 로컬에서는 환경 변수 `MOCHI_KEYSTORE`, `MOCHI_KEYSTORE_PASSWORD`, `MOCHI_KEY_ALIAS`, `MOCHI_KEY_PASSWORD`를 주고 `./gradlew :app:assembleRelease`를 실행합니다. 값이 없으면 확인용으로 디버그 키로 서명합니다. 그 APK는 배포하면 안 됩니다.

## 외형 기준

키 크기·간격·색·아이콘 크기는 iOS 27 시뮬레이터(iPhone 17, 폭 402pt)의 키보드를 픽셀 단위로 재서 맞췄습니다. 값은 `app/.../theme/ThemeTokens.kt`에 모여 있습니다. 측정에 쓴 화면 캡처는 저장소에 넣지 않습니다.
