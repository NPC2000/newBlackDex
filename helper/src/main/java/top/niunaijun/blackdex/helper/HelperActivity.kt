package top.niunaijun.blackdex.helper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

class HelperActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle("BlackDex32 Helper")
            .setMessage(
                "32位辅助程序\n\n" +
                "此程序在后台运行，用于脱壳32位应用。\n" +
                "无需手动操作，请保持安装状态。"
            )
            .setPositiveButton("卸载") { _, _ ->
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                startActivity(intent)
                finish()
            }
            .setNegativeButton("关闭") { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }
}
