# OneCore SDK Engine

A comprehensive, pure Java Android Loader SDK designed for stealth, virtualization, and advanced instrumentation. Supports Android 8.0 (API 26) through Android 14 (API 34).

## 🛠️ Admin & Deployment

This project includes a complete license management system and admin dashboard.

### 1. Admin Panel
Located in `/admin/`. You can host this using **GitHub Pages**.
- **Login**: `admin` / `admin123` (editable in `admin/index.html`)
- **Features**: Customer CRUD, Sales tracking, Backup/Restore, Revenue analytics.

### 2. Server API
Located in `/server/`. Upload the contents of this folder to any **PHP hosting** provider.
- **Verification Endpoint**: `https://your-host.com/server/verify.php?key=YOUR_KEY`
- **Database**: Uses `admin/data/customers.json` as a flat-file database.

### 3. SDK Configuration
In `app/src/main/java/com/onecore/sdk/SDKLicense.java`, update the `URL` to point to your hosted `verify.php` endpoint.

---

## 🚀 Features (Core SDK)

- **Virtual Container**: Sandbox-based application cloning and system service proxying.
- **Enhanced Hook Engine**: Reflection-based method interception for interfaces and arbitrary objects.
- **Hardware Spoofing**: Real-time masking of device IMEI, Serial, Model, and Build info.
- **GPS Mocking**: Native system-level location spoofing via `setTestProviderLocation`.
- **Memory Operations**: High-speed memory read/write access via `/proc/[pid]/mem`.
- **Anti-Detection**: Built-in guards against debuggers, emulators, root access, and system tampering.
- **Floating Overlay**: In-game control menu for real-time feature toggling.
- **Production Utilities**: Global crash handling, remote configuration, and version auto-updating.

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
