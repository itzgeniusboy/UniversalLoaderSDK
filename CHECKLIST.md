# Pre-Push Verification Checklist

Follow these steps to ensure a flawless GitHub build on the first attempt.

## 1. Project Structure Check [ ]
Ensure the directory structure matches accurately:
- [ ] `build.gradle` (Root)
- [ ] `settings.gradle`
- [ ] `gradle.properties`
- [ ] `app/build.gradle`
- [ ] `app/src/main/AndroidManifest.xml`
- [ ] `app/src/main/java/com/onecore/sdk/...` (All 13 Java classes)
- [ ] `.github/workflows/build.yml`

## 2. Code Verification [ ]
- [ ] Check `LoaderSDK.java`: Generic imports removed, local package stubs only.
- [ ] No `TODO` comments or `System.out.println` remain.

## 3. Common Build Failures & Fixes
| Error | Cause | Fix |
|---|---|---|
| `gradle: command not found` | Workflow environment issue | Use `gradle` (system) or include `gradlew` wrapper |
| `Namespace not found` | Android Gradle Plugin v8+ requirement | Ensure `namespace 'com.onecore.sdk'` is in `app/build.gradle` |
| `Java Version Mismatch` | JDK 17 used on JDK 11 code | Project is compatible with JDK 17, no fix needed |
| `Permission Denied` | `gradlew` permissions | Workflow includes `chmod +x` step |

## 4. Build Success Confirmation
1. GitHub Action Run status: **SUCCESS** (Green).
2. Artifact produced: `onecore-v1.0.4`.
3. Size: Approximately 30KB - 100KB (Pure Java size is small).

## 5. Integration Test
Import the `.aar` into an empty project. If it compiles, the build is perfect.
