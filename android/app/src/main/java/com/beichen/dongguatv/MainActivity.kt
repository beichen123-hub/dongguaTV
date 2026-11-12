package com.beichen.dongguatv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

    private var current: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 悬浮液态玻璃底栏动态避让系统导航栏手势条
        val baseBottomMargin = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.navGlassBar) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = navBottom + baseBottomMargin
            v.layoutParams = lp
            insets
        }

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
