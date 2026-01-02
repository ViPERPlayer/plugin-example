package com.viperplayer.plugin.example

import android.content.Context
import com.viperplayer.plugin.ViperPlugin
import com.viperplayer.plugin.ViperPluginService

/**
 * Service that exposes the DemoPlugin to the host app.
 * 
 * This is the entry point that the host app binds to.
 */
class DemoPluginService : ViperPluginService() {
    
    override fun createPlugin(context: Context): ViperPlugin = DemoPlugin()
}

