package top.niunaijun.blackdex.helper

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import top.niunaijun.blackbox.BlackDexCore
import top.niunaijun.blackbox.core.system.dump.IBDumpMonitor
import top.niunaijun.blackbox.entity.dump.DumpResult
import top.niunaijun.blackbox.service.IDumpCallback
import top.niunaijun.blackbox.service.IDumpService

class DumpService : Service() {

    companion object {
        private const val TAG = "DumpService"
    }

    private var callback: IDumpCallback? = null
    private var monitorRegistered = false

    private val monitor = object : IBDumpMonitor.Stub() {
        override fun onDump(result: DumpResult?) {
            result?.let {
                try {
                    callback?.onDump(it)
                } catch (e: Exception) {
                    Log.e(TAG, "onDump callback failed", e)
                }
            }
        }
    }

    private val binder = object : IDumpService.Stub() {
        override fun isReady(): Boolean {
            return true
        }

        override fun startDump(
            packageName: String,
            dumpDir: String,
            fixMethod: Boolean,
            hookDump: Boolean,
            autoCallMethod: Boolean,
            verifyDex: Boolean,
            cb: IDumpCallback?
        ): Boolean {
            HelperApp.dumpDir = dumpDir
            HelperApp.fixMethod = fixMethod
            HelperApp.hookDump = hookDump
            HelperApp.autoCallMethod = autoCallMethod
            HelperApp.verifyDex = verifyDex
            callback = cb

            if (!monitorRegistered) {
                BlackDexCore.get().registerDumpMonitor(monitor)
                monitorRegistered = true
            }

            Log.i(TAG, "startDump: $packageName, dir=$dumpDir")
            val result = BlackDexCore.get().dumpDex(packageName)
            if (result == null) {
                Log.e(TAG, "dumpDex returned null for $packageName")
                try {
                    callback?.onDump(DumpResult().apply {
                        this.packageName = packageName
                        dumpError("Helper: install or launch failed")
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "error callback failed", e)
                }
                return false
            }
            return true
        }

        override fun isRunning(): Boolean {
            return BlackDexCore.get().isRunning
        }

        override fun cancel() {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        val callerUid = Binder.getCallingUid()
        try {
            val callerPkg = packageManager.getNameForUid(callerUid)
            if (callerPkg != null) {
                val callerSig = packageManager.getPackageInfo(callerPkg, PackageManager.GET_SIGNATURES).signatures
                val mySig = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                if (callerSig != null && mySig != null && callerSig.contentEquals(mySig)) {
                    Log.i(TAG, "onBind: accepted (uid=$callerUid, pkg=$callerPkg)")
                    return binder
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBind: signature check failed", e)
        }
        Log.w(TAG, "onBind: rejected (uid=$callerUid)")
        return null
    }
}
