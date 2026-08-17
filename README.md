# plugin-example

A minimal **music-source** plugin for [ViPER Player](https://github.com/ViPERPlayer/viperplayer),
kept deliberately small so the shape of a plugin is visible at a glance.

Two files:

- **`DemoPluginService`** — the entry point the host binds to. It declares the plugin's identity and
  registers the providers it offers. The SDK derives the advertised capability manifest from what is
  registered, so the host only ever calls verbs the plugin actually implements.
- **`DemoSource`** — a `SourceProvider` serving a small in-memory catalog: search, browse, and stream
  resolution.

## How the host finds it

Install the APK. The host enumerates every package exposing
`com.viperplayer.plugin.ViperPluginService` and offers it under Settings → Plugins — there is no
registry and no allowlist.

```xml
<service android:name=".DemoPluginService" android:exported="true">
    <intent-filter>
        <action android:name="com.viperplayer.plugin.ViperPluginService" />
    </intent-filter>
</service>
```

## Building

```bash
./gradlew :plugin-example:assembleDebug
```

It depends on the published SDK, so nothing else is needed. To build against a local SDK checkout:

```bash
./gradlew :plugin-example:assembleDebug -Pviper.pluginSdk.dir=../plugin-sdk
```

## See also

- [plugin-sdk](https://github.com/ViPERPlayer/plugin-sdk) — the contract, and the design notes behind
  the frozen wire ABI
- [dsp-example](https://github.com/ViPERPlayer/dsp-example) — the same idea for an audio effect
