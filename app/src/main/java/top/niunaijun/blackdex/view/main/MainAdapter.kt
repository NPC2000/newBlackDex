package top.niunaijun.blackdex.view.main

import android.view.View
import android.view.ViewGroup
import top.niunaijun.blackbox.utils.AbiUtils
import top.niunaijun.blackdex.data.entity.AppInfo
import top.niunaijun.blackdex.databinding.ItemPackageBinding
import top.niunaijun.blackdex.util.newBindingViewHolder
import top.niunaijun.blackdex.view.base.BaseAdapter

class MainAdapter : BaseAdapter<ItemPackageBinding, AppInfo>() {
    override fun getViewBinding(parent: ViewGroup): ItemPackageBinding {
        return newBindingViewHolder(parent, false)
    }

    override fun initView(binding: ItemPackageBinding, position: Int, data: AppInfo) {
        binding.icon.setImageDrawable(data.icon)
        binding.name.text = data.name
        binding.packageName.text = data.packageName
        when (data.abiType) {
            AbiUtils.AbiType.ARM32_ONLY -> {
                binding.abiTag.text = "32位"
                binding.abiTag.visibility = View.VISIBLE
            }
            AbiUtils.AbiType.BOTH -> {
                binding.abiTag.text = "32/64位"
                binding.abiTag.visibility = View.VISIBLE
            }
            AbiUtils.AbiType.ARM64_ONLY, AbiUtils.AbiType.NONE -> {
                binding.abiTag.visibility = View.GONE
            }
        }
    }
}