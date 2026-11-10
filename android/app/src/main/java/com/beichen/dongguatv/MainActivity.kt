package com.beichen.dongguatv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始显示首页
        switchTo(homeFragment)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchTo(homeFragment); true }
                R.id.nav_search -> { switchTo(searchFragment); true }
                R.id.nav_mine -> { switchTo(mineFragment); true }
                else -> false
            }
        }
    }

    private fun switchTo(target: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHost, target)
            .commitAllowingStateLoss()
    }
}
