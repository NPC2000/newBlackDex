package top.niunaijun.blackdex.view.setting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.folderChooser
import top.niunaijun.blackdex.app.App
import top.niunaijun.blackdex.R
import top.niunaijun.blackdex.app.AppManager
import top.niunaijun.blackdex.app.BlackDexLoader
import top.niunaijun.blackdex.util.HelperManager
import java.io.File


class SettingFragment : PreferenceFragmentCompat() {

    private lateinit var savePathPreference: Preference

    private lateinit var saveEnablePreference: SwitchPreferenceCompat

    private lateinit var fixCodeItemPreference: SwitchPreferenceCompat

    private lateinit var autoCallMethodPreference: SwitchPreferenceCompat

    private lateinit var hookDumpPreference: SwitchPreferenceCompat

    private lateinit var verifyDexPreference: SwitchPreferenceCompat

    private lateinit var enable32BitPreference: SwitchPreferenceCompat

    private lateinit var installHelperPreference: Preference

    private lateinit var dualArchPreference: SwitchPreferenceCompat

    private val initialDirectory = AppManager.mBlackBoxLoader.getSavePath()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.setting)
        savePathPreference = findPreference("save_path")!!
        savePathPreference.onPreferenceClickListener = mSavedPathClick
        savePathPreference.summary = initialDirectory

        saveEnablePreference = findPreference("save_enable")!!
        saveEnablePreference.onPreferenceChangeListener = mSaveEnableChange
        saveEnablePreference.isChecked = AppManager.mBlackBoxLoader.saveEnable()

        fixCodeItemPreference = findPreference("fix_code_item")!!
        fixCodeItemPreference.onPreferenceChangeListener = mFixCodeItemChange
        fixCodeItemPreference.isChecked = AppManager.mBlackBoxLoader.isFixCodeItem()

        hookDumpPreference = findPreference("hook_dump")!!
        hookDumpPreference.onPreferenceChangeListener = mHookDumpChange
        hookDumpPreference.isChecked = AppManager.mBlackBoxLoader.isHookDump()

        autoCallMethodPreference = findPreference("auto_call_method")!!
        autoCallMethodPreference.onPreferenceChangeListener = mAutoCallMethodChange
        autoCallMethodPreference.isChecked = AppManager.mBlackBoxLoader.isAutoCallMethod()

        verifyDexPreference = findPreference("verify_dex")!!
        verifyDexPreference.onPreferenceChangeListener = mVerifyDexChange
        verifyDexPreference.isChecked = AppManager.mBlackBoxLoader.isVerifyDex()

        enable32BitPreference = findPreference("enable_32bit")!!
        enable32BitPreference.onPreferenceChangeListener = mEnable32BitChange
        enable32BitPreference.isChecked = AppManager.mBlackBoxLoader.is32BitCompat()

        installHelperPreference = findPreference("install_helper")!!
        installHelperPreference.onPreferenceClickListener = mInstallHelperClick
        updateHelperStatus()

        dualArchPreference = findPreference("dual_arch_dump")!!
        dualArchPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            AppManager.mBlackBoxLoader.setDualArchDump(newValue as Boolean)
            true
        }
        dualArchPreference.isChecked = AppManager.mBlackBoxLoader.isDualArchDump()
    }

    override fun onResume() {
        super.onResume()
        updateHelperStatus()
    }

    private fun updateHelperStatus() {
        val installed = HelperManager.isHelperInstalled(requireContext())
        installHelperPreference.summary = if (installed) {
            val version = HelperManager.getHelperVersionCode(requireContext())
            if (version < HelperManager.REQUIRED_HELPER_VERSION) {
                "Helper v$version (outdated, tap to update)"
            } else {
                getString(R.string.helper_installed) + " v$version"
            }
        } else {
            getString(R.string.helper_not_installed)
        }
    }

    private val mEnable32BitChange = Preference.OnPreferenceChangeListener { _, newValue ->
        AppManager.mBlackBoxLoader.set32BitCompat(newValue as Boolean)
        true
    }

    private val mInstallHelperClick = Preference.OnPreferenceClickListener {
        if (HelperManager.isHelperInstalled(requireContext())) {
            MaterialDialog(requireContext()).show {
                title(R.string.install_helper)
                message(R.string.helper_installed)
                positiveButton(R.string.confirm) {
                    HelperManager.installHelper(requireContext())
                }
                negativeButton(R.string.cancel)
            }
        } else {
            HelperManager.installHelper(requireContext())
        }
        true
    }

    private val mSavedPathClick = Preference.OnPreferenceClickListener {
        val initialFile = with(initialDirectory) {
            if (initialDirectory.isEmpty()) {
                Environment.getExternalStorageDirectory()
            } else {
                File(this)
            }
        }

        MaterialDialog(requireContext()).show {
            folderChooser(
                requireContext(),
                initialDirectory = initialFile,
                allowFolderCreation = true
            ) { _, file ->
                AppManager.mBlackBoxLoader.setSavePath(file.absolutePath)
                savePathPreference.summary = file.absolutePath
            }
            negativeButton(res = R.string.cancel)
        }
        return@OnPreferenceClickListener true
    }

    private val mSaveEnableChange = Preference.OnPreferenceChangeListener { _, newValue ->
        if (newValue == false) {
            (requireActivity() as SettingActivity).setRequestCallback(requestResult)
        } else {
            AppManager.mBlackBoxLoader.saveEnable(true)
            saveEnablePreference.isChecked = true
        }
        return@OnPreferenceChangeListener true
    }

    private val mHookDumpChange = Preference.OnPreferenceChangeListener { _, newValue ->
        AppManager.mBlackBoxLoader.setHookDump(newValue as Boolean)
        return@OnPreferenceChangeListener true
    }

    private val mFixCodeItemChange = Preference.OnPreferenceChangeListener { _, newValue ->
        if (newValue as Boolean) {

            MaterialDialog(requireContext()).show {
                title(R.string.warn)
                message(R.string.fix_code_item_message)
                positiveButton(R.string.confirm) {
                    AppManager.mBlackBoxLoader.setFixCodeItem(true)
                }
                negativeButton(R.string.cancel) {
                    fixCodeItemPreference.isChecked = false
                    AppManager.mBlackBoxLoader.setFixCodeItem(false)
                }
            }

        } else {
            AppManager.mBlackBoxLoader.setFixCodeItem(newValue)
        }
        return@OnPreferenceChangeListener true
    }

    private val mAutoCallMethodChange = Preference.OnPreferenceChangeListener { _, newValue ->
        AppManager.mBlackBoxLoader.setAutoCallMethod(newValue as Boolean)
        return@OnPreferenceChangeListener true
    }

    private val mVerifyDexChange = Preference.OnPreferenceChangeListener { _, newValue ->
        AppManager.mBlackBoxLoader.setVerifyDex(newValue as Boolean)
        return@OnPreferenceChangeListener true
    }


    private val requestResult = { hasPermission: Boolean ->
        AppManager.mBlackBoxLoader.saveEnable(!hasPermission)
        saveEnablePreference.isChecked = !hasPermission

        if (AppManager.mBlackBoxLoader.getSavePath().isEmpty()) {
            val path = BlackDexLoader.getDexDumpDir(App.getContext())
            AppManager.mBlackBoxLoader.setSavePath(path)
            savePathPreference.summary = path
        }
    }
}
