package dev.metro.launcher

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.metro.launcher.data.AutoUpdateNotificationHelper
import dev.metro.launcher.data.ReleaseInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoUpdateNotificationTest {

    @Test
    fun testNotificationChannelCreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AutoUpdateNotificationHelper.createNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(AutoUpdateNotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertTrue(channel.name.toString().isNotBlank())
    }

    @Test
    fun testShowUpdateNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val ui = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            ui.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            ui.grantRuntimePermission(testContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val release = ReleaseInfo(
            tagName = "v9.9.9",
            versionName = "9.9.9",
            apkDownloadUrl = "https://example.com/app.apk",
            changelog = "Test changelog",
            apkSize = 1024 * 1024L,
        )
        assertTrue("canPostNotifications must be true", AutoUpdateNotificationHelper.canPostNotifications(context))
        AutoUpdateNotificationHelper.showUpdateNotification(context, release)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(AutoUpdateNotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
    }
}
