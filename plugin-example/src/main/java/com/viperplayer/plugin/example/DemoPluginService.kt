package com.viperplayer.plugin.example

import android.content.Context
import com.viperplayer.plugin.model.SourceCapabilities
import com.viperplayer.plugin.author.PluginRegistration
import com.viperplayer.plugin.author.ViperPluginService
import com.viperplayer.plugin.author.pluginRegistration

/**
 * Entry point the host binds to. It declares the plugin's identity and registers the providers it
 * offers — here a single [DemoSource]. The SDK derives the advertised manifest from what's
 * registered, so the host only ever calls capabilities the plugin actually implements.
 */
class DemoPluginService : ViperPluginService() {

    override fun onCreatePlugin(context: Context): PluginRegistration {
        val source = DemoSource()
        return pluginRegistration(
            id = "com.viperplayer.plugin.example",
            name = "Demo Music Plugin",
            version = "1.0",
        ) {
            description("A demo plugin with sample music for testing")
            source(
                source,
                SourceCapabilities(
                    search = true,
                    searchSuggestions = true,
                    browse = true,
                    library = true,
                    playlists = true,
                    home = true,
                    canSeek = true,
                ),
            )
            onShutdown { source.shutdown() }
        }
    }
}
