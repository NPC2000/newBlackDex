package top.niunaijun.blackdex.helper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import top.niunaijun.blackbox.BlackDexCore
import top.niunaijun.blackbox.core.system.dump.IBDumpMonitor
import top.niunaijun.blackbox.entity.dump.DumpResult

class DumpLauncherActivity : Activity() {

    companion object {
        private const val TAG = "DumpLauncher"
        const val EXTRA_PACKAGE = "packageName"
        const val EXTRA_DUMP_DIR = "dumpDir"
        const val EXTRA_FIX_METHOD = "fixMethod"
        const val EXTRA_HOOK_DUMP = "hookDump"
        const val EXTRA_AUTO_CALL = "autoCallMethod"
        const val EXTRA_VERIFY_DEX = "verifyDex"
        const val EXTRA_CALLER_PKG = "callerPkg"
        const val EXTRA_SUB_DIR = "subDir"
        const val ACTION_DUMP_STATUS = "top.niunaijun.blackdex.DUMP_STATUS"
    }

    private var callerPkg: String = ""
    private var monitorRegistered = false
    private var targetPackage: String = ""
    private var passedDumpDir: String = ""

    private val monitor = object : IBDumpMonitor.Stub() {
        override fun onDump(result: DumpResult?) {
            Log.i(TAG, "onDump: ${result?.packageName} success=${result?.isSuccess}")
            sendStatusBroadcast(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        passedDumpDir = intent.getStringExtra(EXTRA_DUMP_DIR) ?: ""
        val fixMethod = intent.getBooleanExtra(EXTRA_FIX_METHOD, false)
        val hookDump = intent.getBooleanExtra(EXTRA_HOOK_DUMP, true)
        val autoCallMethod = intent.getBooleanExtra(EXTRA_AUTO_CALL, false)
        val verifyDex = intent.getBooleanExtra(EXTRA_VERIFY_DEX, true)
        callerPkg = intent.getStringExtra(EXTRA_CALLER_PKG) ?: ""

        if (targetPackage.isEmpty() || passedDumpDir.isEmpty()) {
            Log.e(TAG, "Missing extras")
            finish()
            return
        }

        HelperApp.fixMethod = fixMethod
        HelperApp.hookDump = hookDump
        HelperApp.autoCallMethod = autoCallMethod
        HelperApp.verifyDex = verifyDex

        val helperOwnDir = "${getExternalFilesDir(null)?.absolutePath}/dexDump"
        val actualDumpDir = tryWriteTest(passedDumpDir, helperOwnDir)
        Log.i(TAG, "Using dump dir: $actualDumpDir (shared: $passedDumpDir, own: $helperOwnDir)")

        HelperApp.saveConfig(this, actualDumpDir, intent.getStringExtra(EXTRA_SUB_DIR) ?: "",
            fixMethod, hookDump, autoCallMethod, verifyDex)

        sendStatusBroadcast(DumpResult().apply {
            packageName = targetPackage
            dir = "$actualDumpDir/$targetPackage"
        })

        startDumpDelayed()
    }

    private fun startDumpDelayed() {
        Log.i(TAG, "startDump delayed 2s: pkg=$targetPackage, dir=${HelperApp.dumpDir}, caller=$callerPkg")

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!monitorRegistered) {
                    BlackDexCore.get().registerDumpMonitor(monitor)
                    monitorRegistered = true
                }
                val result = BlackDexCore.get().dumpDex(targetPackage)
                if (result == null) {
                    sendStatusBroadcast(DumpResult().apply {
                        packageName = targetPackage
                        dumpError("Helper: install or launch failed")
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "dumpDex failed", e)
                sendStatusBroadcast(DumpResult().apply {
                    packageName = targetPackage
                    dumpError("Helper: ${e.javaClass.simpleName}: ${e.message}")
                })
            }
            finish()
        }, 2000)
    }

    private fun tryWriteTest(sharedDir: String, fallbackDir: String): String {
        return try {
            val testFile = java.io.File(sharedDir, ".write_test")
            testFile.parentFile?.mkdirs()
            testFile.writeText("test")
            testFile.delete()
            Log.i(TAG, "Shared dir writable: $sharedDir")
            sharedDir
        } catch (e: Exception) {
            Log.w(TAG, "Shared dir not writable: $sharedDir, using fallback: $fallbackDir")
            fallbackDir
        }
    }

    private fun sendStatusBroadcast(result: DumpResult?) {
        if (callerPkg.isEmpty()) return
        try {
            val intent = Intent(ACTION_DUMP_STATUS).apply {
                setPackage(callerPkg)
                putExtra("status", when {
                    result?.isSuccess == true -> 1
                    result?.isFail == true -> 2
                    else -> 0
                })
                putExtra("packageName", result?.packageName ?: "")
                putExtra("dir", result?.dir ?: "")
                putExtra("msg", result?.msg ?: "")
                putExtra("currProcess", result?.currProcess ?: 0)
                putExtra("totalProcess", result?.totalProcess ?: 0)
            }
            sendBroadcast(intent)
            Log.i(TAG, "sendStatusBroadcast: status=${intent.getIntExtra("status", -1)}")
        } catch (e: Exception) {
            Log.e(TAG, "sendStatusBroadcast failed", e)
        }
    }
}
