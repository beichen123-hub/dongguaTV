package com.beichen.dongguatv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.beichen.dongguatv.databinding.ActivityMainBinding
import com.beichen.dongguatv.ui.home.HomeFragment
import com.beichen.dongguatv.ui.mine.MineFragment
import com.beichen.dongguatv.ui.search.SearchFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment by lazy { HomeFragment() }
    private val searchFragment by lazy { SearchFragment() }
    private val mineFragment by lazy { MineFragment() }

    /** 当前展示的 Fragment。用 add/show/hide 而不是 replace，
     *  replace 会销毁重建，切回首页等于重新发 6 个搜索请求 + 重新闪 loading。 */
    private var current: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        switchTo(homeFragment)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchTo(homeFragment); true }
                R.id.nav_search -> { switchTo(searchFragment); true }
                R.id.nav_mine -> { switchTo(mineFragment); true }
                else -> false
            }
        }

        requestNotificationPermission()
    }

    private fun switchTo(target: Fragment) {
        if (current === target) return
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        current?.let { if (it.isAdded) ft.hide(it) }
        if (target.isAdded) ft.show(target) else ft.add(R.id.navHost, target)
        ft.commitAllowingStateLoss()
        current = target
    }

    /** Android 13+ 通知要运行时授权，不申请的话追剧提醒永远不弹 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
