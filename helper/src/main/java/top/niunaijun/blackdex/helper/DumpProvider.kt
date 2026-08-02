package top.niunaijun.blackdex.helper

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log
import top.niunaijun.blackbox.BlackDexCore
import top.niunaijun.blackbox.core.system.dump.IBDumpMonitor
import top.niunaijun.blackbox.entity.dump.DumpResult

class DumpProvider : ContentProvider() {

    companion object {
        private const val TAG = "DumpProvider"
        const val AUTHORITY = "top.niunaijun.blackdex32helper.dump"
        const val METHOD_START = "start"
        const val METHOD_STATUS = "status"
        const val METHOD_IS_RUNNING = "isRunning"
    }

    private var dumpResult: DumpResult? = null
    private var monitorRegistered = false

    private val monitor = object : IBDumpMonitor.Stub() {
        override fun onDump(result: DumpResult?) {
            Log.i(TAG, "onDump: ${result?.packageName}, status=${result?.isSuccess}")
            dumpResult = result
        }
    }

    override fun onCreate(): Boolean {
        Log.i(TAG, "DumpProvider onCreate")
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        Log.i(TAG, "call: method=$method, arg=$arg")

        if (!checkSignature()) {
            Log.w(TAG, "Rejected: signature mismatch")
            return null
        }

        when (method) {
            METHOD_START -> {
                val packageName = arg ?: return null
                val dumpDir = extras?.getString("dumpDir") ?: return null
                val fixMethod = extras?.getBoolean("fixMethod", false) ?: false
                val hookDump = extras?.getBoolean("hookDump", true) ?: true
                val autoCallMethod = extras?.getBoolean("autoCallMethod", false) ?: false
                val verifyDex = extras?.getBoolean("verifyDex", true) ?: true

                HelperApp.dumpDir = dumpDir
                HelperApp.fixMethod = fixMethod
                HelperApp.hookDump = hookDump
                HelperApp.autoCallMethod = autoCallMethod
                HelperApp.verifyDex = verifyDex
                dumpResult = null

                if (!monitorRegistered) {
                    BlackDexCore.get().registerDumpMonitor(monitor)
                    monitorRegistered = true
                }

                Log.i(TAG, "startDump: pkg=$packageName, dir=$dumpDir")
                val result = BlackDexCore.get().dumpDex(packageName)
                val bundle = Bundle()
                bundle.putBoolean("started", result != null)
                return bundle
            }
            METHOD_STATUS -> {
                val bundle = Bundle()
                dumpResult?.let {
                    bundle.putParcelable("result", it)
                }
                return bundle
            }
            METHOD_IS_RUNNING -> {
                val bundle = Bundle()
                bundle.putBoolean("running", BlackDexCore.get().isRunning)
                return bundle
            }
        }
        return null
    }

    private fun checkSignature(): Boolean {
        val ctx = context ?: return false
        val callerUid = Binder.getCallingUid()
        return try {
            val callerPkg = ctx.packageManager.getNameForUid(callerUid) ?: return false
            val callerSig = ctx.packageManager.getPackageInfo(callerPkg, PackageManager.GET_SIGNATURES).signatures
            val mySig = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            callerSig != null && mySig != null && callerSig.contentEquals(mySig)
        } catch (e: Exception) {
            Log.e(TAG, "checkSignature failed", e)
            false
        }
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, sa: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<String>?): Int = 0
}
