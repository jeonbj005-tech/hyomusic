package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.util.ReleaseVersionUtil

class NewVersionWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     *
     * @param versionName    Name of new version
     * @param apkLocationUrl Url with the new apk
     * @param versionCode    Code of new version
     */
    private fun compareAppVersionAndShowNotification(
        versionName: String,
        apkLocationUrl: String?,
        versionCode: Int
    ) {
        if (BuildConfig.VERSION_CODE >= versionCode) {
            resultData = workDataOf(UPDATE_AVAILABLE to false)
            return
        }
        resultData = workDataOf(
            UPDATE_AVAILABLE to true,
            VERSION_NAME to versionName,
            APK_URL to (apkLocationUrl ?: "")
        )

        // A pending intent to open the apk location url in the browser.
        val intent = Intent(Intent.ACTION_VIEW, apkLocationUrl?.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntentCompat.getActivity(
            applicationContext,
            0,
            intent,
            0,
            false
        )
        val channelId = applicationContext.getString(R.string.app_update_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_newpipe_update)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(
                applicationContext.getString(R.string.app_update_available_notification_title)
            )
            .setContentText(
                applicationContext.getString(
                    R.string.app_update_available_notification_text,
                    versionName
                )
            )

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(2000, notificationBuilder.build())
        }
    }

    private var resultData = workDataOf(CHECK_FAILED to true)

    @Throws(IOException::class, ReCaptchaException::class)
    private fun checkNewVersion() {
        if (!inputData.getBoolean(IS_MANUAL, false)) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            // Check if the last request has happened a certain time ago
            // to reduce the number of API requests.
            val expiry = prefs.getLong(applicationContext.getString(R.string.update_expiry_key), 0)
            if (!ReleaseVersionUtil.isLastUpdateCheckExpired(expiry)) {
                return
            }
        }

        // Read the SimpleTunes manifest from the GitHub branch.
        val response = DownloaderImpl.getInstance().get(SIMPLE_TUNES_UPDATE_URL)
        handleResponse(response)
    }

    private fun handleResponse(response: Response) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        try {
            // Store a timestamp which needs to be exceeded,
            // before a new request to the API is made.
            val newExpiry = ReleaseVersionUtil.coerceUpdateCheckExpiry(response.getHeader("expires"))
            prefs.edit {
                putLong(applicationContext.getString(R.string.update_expiry_key), newExpiry)
            }
        } catch (e: Exception) {
            if (DEBUG) {
                Log.w(TAG, "Could not extract and save new expiry date", e)
            }
        }

        // Parse the json from the response.
        try {
            val latestVersionInfo = JsonParser.`object`().from(response.responseBody())
                .getObject("latest")
            val versionName = latestVersionInfo.getString("version_name")
            val versionCode = latestVersionInfo.getInt("version_code")
            val apkLocationUrl = latestVersionInfo.getString("apk")
            val apkUri = apkLocationUrl?.toUri()
            if (versionName.isNullOrBlank() || apkUri?.scheme != "https" ||
                apkUri.host != "github.com"
            ) {
                return
            }
            compareAppVersionAndShowNotification(versionName, apkLocationUrl, versionCode)
        } catch (e: JsonParserException) {
            // Most likely something is wrong in data received from NEWPIPE_API_URL.
            // Do not alarm user and fail silently.
            if (DEBUG) {
                Log.w(TAG, "Could not get SimpleTunes update manifest: invalid json", e)
            }
        }
    }

    override fun doWork(): Result {
        return try {
            checkNewVersion()
            Result.success(resultData)
        } catch (e: IOException) {
            Log.w(TAG, "Could not fetch NewPipe API: probably network problem", e)
            Result.failure()
        } catch (e: ReCaptchaException) {
            Log.e(TAG, "ReCaptchaException should never happen here.", e)
            Result.failure()
        }
    }

    companion object {
        private val DEBUG = MainActivity.DEBUG
        private val TAG = NewVersionWorker::class.java.simpleName
        private const val SIMPLE_TUNES_UPDATE_URL =
            "https://raw.githubusercontent.com/jeonbj005-tech/hyomusic/hyomusic/update.json"
        private const val IS_MANUAL = "isManual"
        const val UPDATE_AVAILABLE = "updateAvailable"
        const val VERSION_NAME = "versionName"
        const val APK_URL = "apkUrl"
        const val CHECK_FAILED = "checkFailed"

        /**
         * Start a new worker which checks if all conditions for performing a version check are met,
         * fetches the GitHub manifest containing the latest SimpleTunes version and displays a
         * notification about an available update if one is available.
         * <br></br>
         * Following conditions need to be met, before data is requested from the server:
         *
         *  * The user enabled searching for and notifying about updates in the settings.
         *  * The app did not recently check for updates.
         * We do not want to make unnecessary connections and DOS our servers.
         */
        @JvmStatic
        fun enqueueNewVersionCheckingWork(context: Context, isManual: Boolean): java.util.UUID {
            val workRequest = OneTimeWorkRequestBuilder<NewVersionWorker>()
                .setInputData(workDataOf(IS_MANUAL to isManual))
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            return workRequest.id
        }
    }
}
