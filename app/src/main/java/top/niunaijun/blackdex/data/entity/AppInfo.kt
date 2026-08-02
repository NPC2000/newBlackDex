package top.niunaijun.blackdex.data.entity

import android.graphics.drawable.Drawable
import top.niunaijun.blackbox.utils.AbiUtils

data class AppInfo(
        val name:String,
        val packageName:String,
        val icon:Drawable,
        val abiType: AbiUtils.AbiType = AbiUtils.AbiType.ARM64_ONLY
)
