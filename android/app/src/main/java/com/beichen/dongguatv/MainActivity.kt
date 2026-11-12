package com.beichen.dongguatv

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
    private var selectedTab = -1

    // 三个 Tab 的视图引用（自绘，避开 Material3 BottomNavigationView 的 item 挤压问题）
    private val tabRoots: List<LinearLayout> get() = listOf(binding.tabHome, binding.tabSearch, binding.tabMine)
    private val tabIcons: List<ImageView> get() = listOf(binding.tabHomeIcon, binding.tabSearchIcon, binding.tabMineIcon)
    private val tabLabels: List<TextView> get() = listOf(binding.tabHomeLabel, binding.tabSearchLabel, binding.tabMineLabel)

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

        tabRoots.forEachIndexed { index, root ->
            root.setOnClickListener { switchToTab(index) }
        }
        switchToTab(0)

        requestNotificationPermission()
    }

    /** 供首页搜索入口等外部调用，直接切到指定 Tab */
    fun switchToTab(index: Int) {
        if (index !in 0..2 || index == selectedTab) return
        selectedTab = index
        highlightTab(index)
        when (index) {
            0 -> switchTo(homeFragment)
            1 -> switchTo(searchFragment)
            2 -> switchTo(mineFragment)
        }
    }

    private fun highlightTab(index: Int) {
        val active = getColor(R.color.colorPrimaryLight)
        val inactive = getColor(R.color.textSecondary)
        tabRoots.forEachIndexed { i, root ->
            root.setBackgroundResource(
                if (i == index) R.drawable.bg_glass_tab_active else 0
            )
        }
        tabIcons.forEachIndexed { i, icon ->
            icon.imageTintList = ColorStateList.valueOf(if (i == index) active else inactive)
        }
        tabLabels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) active else inactive)
        }
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
