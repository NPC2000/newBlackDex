package top.niunaijun.blackdex.view.main

import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackdex.data.DexDumpRepository
import top.niunaijun.blackdex.data.entity.AppInfo
import top.niunaijun.blackdex.data.entity.DumpInfo
import top.niunaijun.blackdex.view.base.BaseViewModel
import top.niunaijun.blackbox.entity.dump.DumpResult

class MainViewModel(private val repo: DexDumpRepository) : BaseViewModel() {

    val mAppListLiveData = MutableLiveData<List<AppInfo>>()

    val mDexDumpLiveData = MutableLiveData<DumpInfo>()

    val mProgressLiveData = MutableLiveData<DumpResult>()

    fun isDualDumping(): Boolean = repo.isDualDumping

    fun getAppList() {
        launchOnUI {
            repo.getAppList(mAppListLiveData)
        }
    }

    fun startDexDump(source: String) {
        launchOnUI {
            repo.dumpDex(source, mDexDumpLiveData, mProgressLiveData)
        }
    }

    fun dexDumpSuccess() {
        launchOnUI {
            repo.dumpSuccess()
        }
    }

}