package top.niunaijun.blackdex.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import top.niunaijun.blackbox.entity.dump.DumpResult
import java.io.File

object HelperManager {

    private const val TAG = "HelperManager"

    const val HELPER_PACKAGE = "top.niunaijun.blackdex32helper"
    const val HELPER_ASSET_NAME = "helper32.apk"
    const val REQUIRED_HELPER_VERSION = 10
    const val HELPER_DUMP_ACTIVITY = "top.niunaijun.blackdex.helper.DumpLauncherActivity"
    const val ACTION_DUMP_STATUS = "top.niunaijun.blackdex.DUMP_STATUS"

    private var statusReceiver: BroadcastReceiver? = null

    fun isHelperInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(HELPER_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getHelperVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(HELPER_PACKAGE, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            -1
        }
    }

    fun isOverlayGranted(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(HELPER_PACKAGE, 0)
            val uid = packageInfo.applicationInfo.uid
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, HELPER_PACKAGE)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, HELPER_PACKAGE)
            }
            Log.i(TAG, "isOverlayGranted: uid=$uid, mode=$mode")
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "isOverlayGranted failed", e)
            false
        }
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$HELPER_PACKAGE")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installHelper(context: Context) {
        try {
            val apkFile = File(context.cacheDir, HELPER_ASSET_NAME)
            context.assets.open(HELPER_ASSET_NAME).use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "installHelper failed", e)
        }
    }

    fun startDump(
        context: Context,
        packageName: String,
        dumpDir: String,
        subDir: String,
        fixMethod: Boolean,
        hookDump: Boolean,
        autoCallMethod: Boolean,
        verifyDex: Boolean
    ): Boolean {
        val intent = Intent().apply {
            component = ComponentName(HELPER_PACKAGE, HELPER_DUMP_ACTIVITY)
            putExtra("packageName", packageName)
            putExtra("dumpDir", dumpDir)
            putExtra("subDir", subDir)
            putExtra("fixMethod", fixMethod)
            putExtra("hookDump", hookDump)
            putExtra("autoCallMethod", autoCallMethod)
            putExtra("verifyDex", verifyDex)
            putExtra("callerPkg", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

        Log.i(TAG, "startDump via Activity: pkg=$packageName, dir=$dumpDir, caller=${context.packageName}")
        return try {
            context.startActivity(intent)
            Log.i(TAG, "DumpLauncherActivity started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startDump failed", e)
            false
        }
    }

    fun registerStatusReceiver(context: Context, onStatus: (DumpResult) -> Unit) {
        unregisterStatusReceiver(context)
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra("status", -1) ?: -1
                val result = DumpResult().apply {
                    packageName = intent?.getStringExtra("packageName") ?: ""
                    dir = intent?.getStringExtra("dir") ?: ""
                    msg = intent?.getStringExtra("msg") ?: ""
                    currProcess = intent?.getIntExtra("currProcess", 0) ?: 0
                    totalProcess = intent?.getIntExtra("totalProcess", 0) ?: 0
                }
                when (status) {
                    0 -> { /* RUNNING, already set */ }
                    1 -> result.dumpSuccess()
                    2 -> result.dumpError(result.msg)
                }
                Log.i(TAG, "StatusReceiver: status=$status, pkg=${result.packageName}")
                onStatus(result)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, IntentFilter(ACTION_DUMP_STATUS), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(statusReceiver, IntentFilter(ACTION_DUMP_STATUS))
        }
        Log.i(TAG, "StatusReceiver registered")
    }

    fun unregisterStatusReceiver(context: Context) {
        statusReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "unregisterStatusReceiver failed", e)
            }
        }
        statusReceiver = null
    }
}
