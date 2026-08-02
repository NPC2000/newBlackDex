package top.niunaijun.blackdex.app

import android.content.Context
import top.niunaijun.blackbox.BlackDexCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import top.niunaijun.blackbox.utils.FileUtils
import top.niunaijun.blackbox.utils.compat.BuildCompat
import top.niunaijun.blackdex.biz.cache.AppSharedPreferenceDelegate
import java.io.File

/**
 *
 * @Description:
 * @Author: wukaicheng
 * @CreateDate: 2021/5/6 23:38
 */
class BlackDexLoader {


    private var mSavePath by AppSharedPreferenceDelegate(App.getContext(), "")

    private var mSaveEnable by AppSharedPreferenceDelegate(App.getContext(), true)

    private var mFixCodeItem by AppSharedPreferenceDelegate(App.getContext(),false)

    private var mHookDump by AppSharedPreferenceDelegate(App.getContext(),true)

    private var mAutoCallMethod by AppSharedPreferenceDelegate(App.getContext(),false)

    private var mVerifyDex by AppSharedPreferenceDelegate(App.getContext(), true)

    private var m32BitCompat by AppSharedPreferenceDelegate(App.getContext(), false)

    private var mDualArchDump by AppSharedPreferenceDelegate(App.getContext(), false)

    private var mDir = if (mSaveEnable) {
        getDexDumpDir(App.getContext())
    } else {
        mSavePath
    }

    fun addLifecycleCallback() {

    }

    fun attachBaseContext(context: Context) {
        BlackDexCore.get().doAttachBaseContext(context, object : ClientConfiguration() {
            override fun getHostPackageName(): String {
                return context.packageName
            }

            override fun getDexDumpDir(): String {
                return mDir
            }

            override fun getDumpSubDir(): String {
                return if (mDualArchDump) "arm64" else ""
            }

            override fun isFixCodeItem(): Boolean {
                return mFixCodeItem
            }

            override fun isEnableHookDump(): Boolean {
                return mHookDump
            }

            override fun isAutoCallMethod(): Boolean {
                return mAutoCallMethod
            }

            override fun isVerifyDex(): Boolean {
                return mVerifyDex
            }
        })
    }

    fun doOnCreate(context: Context) {
        BlackDexCore.get().doCreate()
    }

    fun saveEnable(): Boolean {
        return mSaveEnable
    }

    fun saveEnable(state: Boolean) {
        this.mSaveEnable = state
    }

    fun getSavePath(): String {
        return mSavePath
    }

    fun setSavePath(path: String) {
        this.mSavePath = path
    }

    fun setFixCodeItem(enable:Boolean){
        this.mFixCodeItem = enable
    }

    fun isFixCodeItem():Boolean{
        return this.mFixCodeItem
    }

    fun setHookDump(enable: Boolean){
        this.mHookDump = enable
    }

    fun isHookDump(): Boolean {

        return this.mHookDump
    }

    fun isAutoCallMethod(): Boolean{
        return this.mAutoCallMethod
    }

    fun setAutoCallMethod(enable: Boolean) {
        this.mAutoCallMethod = enable
    }


    fun isVerifyDex(): Boolean {
        return mVerifyDex
    }

    fun setVerifyDex(enable: Boolean) {
        this.mVerifyDex = enable
    }

    fun is32BitCompat(): Boolean {
        return m32BitCompat
    }

    fun set32BitCompat(enable: Boolean) {
        this.m32BitCompat = enable
    }

    fun isDualArchDump(): Boolean {
        return mDualArchDump
    }

    fun setDualArchDump(enable: Boolean) {
        this.mDualArchDump = enable
    }

    fun getBaseDumpDir(): String {
        return mDir
    }


    companion object {

        val TAG: String = BlackDexLoader::class.java.simpleName

        fun getDexDumpDir(context: Context): String {
            return if (BuildCompat.isR()) {
                val dump = File(
                    context.externalCacheDir?.parentFile?.parentFile?.parentFile?.parentFile,
                    "Download/dexDump"
                )
                FileUtils.mkdirs(dump)
                dump.absolutePath
            } else {
                val dump = File(context.externalCacheDir?.parentFile, "dump")
                FileUtils.mkdirs(dump)
                dump.absolutePath
            }
        }
    }
}