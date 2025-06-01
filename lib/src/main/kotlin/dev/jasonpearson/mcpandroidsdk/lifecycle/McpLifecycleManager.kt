package dev.jasonpearson.mcpandroidsdk.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages MCP server lifecycle in relation to Android application lifecycle.
 *
 * This class automatically starts and stops the MCP server based on application lifecycle events,
 * ensuring proper resource management and server availability.
 */
class McpLifecycleManager private constructor() :
    DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "McpLifecycleManager"

        @Volatile private var INSTANCE: McpLifecycleManager? = null

        fun getInstance(): McpLifecycleManager {
            return INSTANCE
                ?: synchronized(this) { INSTANCE ?: McpLifecycleManager().also { INSTANCE = it } }
        }
    }

    private val lifecycleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isLifecycleObserverRegistered = false
    private var isActivityCallbacksRegistered = false
    private var activeActivities = 0
    private var isAppInBackground = false

    /** Configuration for lifecycle management */
    data class LifecycleConfig(
        val autoStartOnAppStart: Boolean = true,
        val autoStopOnAppStop: Boolean = true,
        val restartOnAppReturn: Boolean = true,
        val pauseOnBackground: Boolean = false,
        val stopOnLastActivityDestroyed: Boolean = false,
    )

    private var config = LifecycleConfig()

    /** Initialize lifecycle management with configuration */
    fun initialize(application: Application, config: LifecycleConfig = LifecycleConfig()) {
        this.config = config

        if (!isLifecycleObserverRegistered) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            isLifecycleObserverRegistered = true
            Log.i(TAG, "Process lifecycle observer registered")
        }

        if (!isActivityCallbacksRegistered) {
            application.registerActivityLifecycleCallbacks(this)
            isActivityCallbacksRegistered = true
            Log.i(TAG, "Activity lifecycle callbacks registered")
        }

        Log.i(TAG, "MCP lifecycle manager initialized with config: $config")
    }

    /** Cleanup lifecycle management */
    fun cleanup() {
        if (isLifecycleObserverRegistered) {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
            isLifecycleObserverRegistered = false
        }
        // Note: We can't unregister activity callbacks without reference to application
        Log.i(TAG, "MCP lifecycle manager cleaned up")
    }

    // ProcessLifecycleOwner callbacks

    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "App started (foreground)")
        isAppInBackground = false

        if (config.autoStartOnAppStart || (config.restartOnAppReturn && !isServerRunning())) {
            startServerIfNeeded()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "App stopped (background)")
        isAppInBackground = true

        if (config.pauseOnBackground) {
            pauseServer()
        } else if (config.autoStopOnAppStop) {
            stopServerIfNeeded()
        }
    }

    // Activity lifecycle callbacks

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.v(TAG, "Activity created: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivities++
        Log.v(
            TAG,
            "Activity started: ${activity.javaClass.simpleName}, active count: $activeActivities",
        )

        if (activeActivities == 1 && config.restartOnAppReturn && !isServerRunning()) {
            startServerIfNeeded()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Log.v(TAG, "Activity resumed: ${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.v(TAG, "Activity paused: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivities--
        Log.v(
            TAG,
            "Activity stopped: ${activity.javaClass.simpleName}, active count: $activeActivities",
        )

        if (activeActivities == 0 && config.stopOnLastActivityDestroyed) {
            stopServerIfNeeded()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.v(TAG, "Activity saving state: ${activity.javaClass.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.v(TAG, "Activity destroyed: ${activity.javaClass.simpleName}")
    }

    // Server management methods

    private fun startServerIfNeeded() {
        lifecycleScope.launch {
            try {
                val manager = McpServerManager.getInstance()
                if (manager.isInitialized() && !manager.isServerRunning()) {
                    Log.i(TAG, "Starting MCP server due to lifecycle event")
                    manager.startServer().onFailure { exception ->
                        Log.e(TAG, "Failed to start MCP server from lifecycle", exception)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server from lifecycle", e)
            }
        }
    }

    private fun stopServerIfNeeded() {
        lifecycleScope.launch {
            try {
                val manager = McpServerManager.getInstance()
                if (manager.isInitialized() && manager.isServerRunning()) {
                    Log.i(TAG, "Stopping MCP server due to lifecycle event")
                    manager.stopServer().onFailure { exception ->
                        Log.e(TAG, "Failed to stop MCP server from lifecycle", exception)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server from lifecycle", e)
            }
        }
    }

    private fun pauseServer() {
        // For now, pausing is the same as stopping
        // In the future, we could implement a pause state that keeps connections alive
        // but stops processing new requests
        stopServerIfNeeded()
    }

    private fun isServerRunning(): Boolean {
        return try {
            val manager = McpServerManager.getInstance()
            manager.isInitialized() && manager.isServerRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server status", e)
            false
        }
    }

    /** Get current lifecycle state information */
    fun getLifecycleState(): LifecycleState {
        return LifecycleState(
            isAppInBackground = isAppInBackground,
            activeActivities = activeActivities,
            isServerRunning = isServerRunning(),
            config = config,
        )
    }

    /** Update lifecycle configuration */
    fun updateConfig(newConfig: LifecycleConfig) {
        this.config = newConfig
        Log.i(TAG, "Updated lifecycle config: $newConfig")
    }

    data class LifecycleState(
        val isAppInBackground: Boolean,
        val activeActivities: Int,
        val isServerRunning: Boolean,
        val config: LifecycleConfig,
    )
}
