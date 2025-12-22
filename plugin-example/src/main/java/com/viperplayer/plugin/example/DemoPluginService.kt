package com.viperplayer.plugin.example

import com.viperplayer.plugin.sdk.ViperPlugin
import com.viperplayer.plugin.sdk.ViperPluginService

/**
 * Service that exposes the DemoPlugin to the host app.
 * 
 * This is the entry point that the host app binds to.
 */
class DemoPluginService : ViperPluginService() {
    
    override fun createPlugin(): ViperPlugin = DemoPlugin()
}

