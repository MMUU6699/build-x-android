package app.buildx.chat

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.core.content.FileProvider
import app.buildx.chat.util.AppForegroundTracker
import app.buildx.chat.util.BuildXSystemNotificationManager

class BuildXApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BuildXSystemNotificationManager.createChannels(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                AppForegroundTracker.onActivityStarted()
            }

            override fun onActivityStopped(activity: Activity) {
                AppForegroundTracker.onActivityStopped()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

class BuildXFileProvider : FileProvider(R.xml.file_paths)
