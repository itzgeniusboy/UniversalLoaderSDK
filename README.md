# OneCore SDK Engine (Autonomous Edition)

A comprehensive, pure Java Android Loader SDK designed for stealth, virtualization, and advanced instrumentation. This edition is fully autonomous and operates without any external panel or server control.

## 🚀 Features (Core SDK)

- **Virtual Container**: Sandbox-based application cloning and system service proxying.
- **Enhanced Hook Engine**: Reflection-based method interception for interfaces and arbitrary objects.
- **Hardware Spoofing**: Real-time masking of device IMEI, Serial, Model, and Build info.
- **GPS Mocking**: Native system-level location spoofing via `setTestProviderLocation`.
- **Memory Operations**: High-speed memory read/write access via `/proc/[pid]/mem`.
- **Anti-Detection**: Built-in guards against debuggers, emulators, root access, and system tampering.
- **Production Utilities**: Global crash handling, local configuration, and offline operation.

## 📦 Integration

### 1. Add the AAR to your project
Copy `onecore-v1.0.4.aar` into your `app/libs/` directory.

### 2. Update build.gradle
Add the following to your application's `build.gradle`:

```groovy
dependencies {
    implementation files('libs/onecore-v1.0.4.aar')
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core:1.12.0'
}
```

### 3. Initialize the SDK
Initialize the SDK in your `Application` class or main Activity:

```java
import com.onecore.sdk.OneCoreSDK;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OneCoreSDK.init(this, "YOUR_KEY");
        OneCoreSDK.install();
    }
}
```

## 📜 Security Policy
This SDK is for educational and research purposes only. The user is responsible for ensuring compliance with all local laws and target application Terms of Service.

## 📄 License
This project is licensed under the Apache 2.0 License.
