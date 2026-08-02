package top.niunaijun.blackdex.helper

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import top.niunaijun.blackbox.BlackDexCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

class HelperApp : Application() {

    companion object {
        private const val PREFS_NAME = "helper_config"
        private const val KEY_DUMP_DIR = "dumpDir"
        private const val KEY_FIX_METHOD = "fixMethod"
        private const val KEY_HOOK_DUMP = "hookDump"
        private const val KEY_AUTO_CALL = "autoCallMethod"
        private const val KEY_VERIFY_DEX = "verifyDex"
        private const val KEY_SUB_DIR = "subDir"

        @Volatile var dumpDir: String = ""
        @Volatile var subDir: String = ""
        @Volatile var fixMethod: Boolean = false
        @Volatile var hookDump: Boolean = true
        @Volatile var autoCallMethod: Boolean = false
        @Volatile var verifyDex: Boolean = true

        fun saveConfig(context: Context, dir: String, sub: String, fix: Boolean, hook: Boolean, auto: Boolean, verify: Boolean) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_DUMP_DIR, dir)
                .putString(KEY_SUB_DIR, sub)
                .putBoolean(KEY_FIX_METHOD, fix)
                .putBoolean(KEY_HOOK_DUMP, hook)
                .putBoolean(KEY_AUTO_CALL, auto)
                .putBoolean(KEY_VERIFY_DEX, verify)
                .apply()
            dumpDir = dir
            subDir = sub
            fixMethod = fix
            hookDump = hook
            autoCallMethod = auto
            verifyDex = verify
        }

        private fun loadConfig(context: Context) {
            val defaultDir = "${context.getExternalFilesDir(null)?.absolutePath}/dexDump"
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            dumpDir = prefs.getString(KEY_DUMP_DIR, defaultDir) ?: defaultDir
            subDir = prefs.getString(KEY_SUB_DIR, "") ?: ""
            fixMethod = prefs.getBoolean(KEY_FIX_METHOD, false)
            hookDump = prefs.getBoolean(KEY_HOOK_DUMP, true)
            autoCallMethod = prefs.getBoolean(KEY_AUTO_CALL, false)
            verifyDex = prefs.getBoolean(KEY_VERIFY_DEX, true)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        loadConfig(base)
        BlackDexCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
            override fun getHostPackageName(): String {
                return base.packageName
            }

            override fun getDexDumpDir(): String {
                return dumpDir
            }

            override fun getDumpSubDir(): String {
                return subDir
            }

            override fun isFixCodeItem(): Boolean {
                return fixMethod
            }

            override fun isEnableHookDump(): Boolean {
                return hookDump
            }

            override fun isAutoCallMethod(): Boolean {
                return autoCallMethod
            }

            override fun isVerifyDex(): Boolean {
                return verifyDex
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        BlackDexCore.get().doCreate()
    }
}
