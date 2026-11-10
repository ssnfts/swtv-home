# TV Hub navigation interface

**Contract 1.0** specifies navigation from SWTV Home to a future, separately installed TV Hub provider. The OwnTV tile uses the launcher’s independent app discovery flow.

## Provider declaration

The provider application ID must be `app.swtv.tvhub`. Declare the required metadata on the **application**, as a literal integer, and one enabled/exported activity handling the action:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <meta-data android:name="app.swtv.tvhub.contractMajor" android:value="1" />
        <activity android:name=".HubActivity" android:enabled="true" android:exported="true">
            <intent-filter>
                <action android:name="app.swtv.tvhub.action.OPEN_HUB" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

The provider's Gradle application ID supplies the expected package. The client checks the value and stored type of the application metadata. A string, a long, absent metadata or another major is incompatible. The application and selected activity must be enabled, and the activity exported. Exactly one eligible handler in the expected package is required. Providers should keep one public navigation entry point across compatible updates.

## Request

| Field | Type | Required value |
| --- | --- | --- |
| Intent action | String | `app.swtv.tvhub.action.OPEN_HUB` |
| Intent package | String | `app.swtv.tvhub` |
| Intent component on launch | ComponentName | The uniquely resolved, validated activity in that package |
| `app.swtv.tvhub.extra.CONTRACT_MAJOR` | Integer extra | `1` |
| `app.swtv.tvhub.extra.DESTINATION` | String extra | `home` |

There are no other allowed request extras or destinations in version 1.0. There is no data URI, MIME type, ClipData, URI permission grant, playlist, stream, account, token, credential, or file attachment. The Android adapter adds `FLAG_ACTIVITY_NEW_TASK` because it retains only an application context.

`HubRequest` is the typed request model. `HubPolicy.allowedRequest` validates the exact extra names, types, major, and destination. `HubIntents.openHub()` builds an inspectable package-restricted request without resolving or launching anything. Supplying a validated `HubHandler` also pins its component. Invalid requests/handlers throw `IllegalArgumentException` at the builder boundary. The app uses `HubClient(AndroidHubGateway(applicationContext))`, which resolves, checks application metadata and eligible handlers, then launches an explicit component and package.

## Results and compatibility

| Client result | Meaning / launcher behavior |
| --- | --- |
| `Missing` | The expected package is absent. The launcher explains that the tile is reserved for the separate future app. |
| `Incompatible` | The required major metadata is absent, malformed, or unsupported. The launcher explains the version requirement. |
| `Unavailable` | Handler selection, request validation, package query or launch failed. The launcher explains that TV Hub cannot open. |
| `Launched` | Android accepted the explicit launch request. |

The launcher always uses the validated component for this shortcut. Package discovery and activity launch can race. Exceptions become `Unavailable`, and the user can retry.

Major versions describe incompatible semantics. The client requires major 1 even if the action and package match. Keep major 1 for additive minor capabilities that preserve the version 1.0 home entry point, required metadata and fields. The version 1.0 client ignores optional provider metadata and sends the same home request. New required fields, destinations or changed meanings need a reviewed contract change. A future client supporting multiple majors must explicitly implement and test each version.

## Reserved future capabilities

M3U ingestion, XMLTV synchronization, channel matching, Media3 playback, cache management and favorites are reserved for future design. They have no actions, destinations, extras, services or permissions in this contract. The current libraries contain no streaming, playlist/EPG processing, network fetch, APK download/install, signing or publishing implementation.

The expected package name and exported activity provide routing. The package name alone cannot establish publisher identity. Any future credential-bearing IPC requires a separately reviewed signing, permission and authentication policy. This contract's request allowlist excludes credentials.

## Validation boundaries

JVM tests exercise the production request, metadata and handler policies, distinct outcomes and launch-race handling through an injected `HubGateway`. App instrumentation inspects the Android intent action, package, allowed extras and validated component, and rejects invalid construction. These intent checks run without launching an external application. The typed passthrough input has its own instrumentation test.
