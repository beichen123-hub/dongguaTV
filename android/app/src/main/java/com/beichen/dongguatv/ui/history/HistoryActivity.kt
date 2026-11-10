package com.beichen.dongguatv.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.databinding.ActivityListBinding
import com.beichen.dongguatv.ui.common.LocalItem
import com.beichen.dongguatv.ui.common.LocalListAdapter
import com.beichen.dongguatv.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private val repo by lazy { Repository.get(this) }
    private val adapter = LocalListAdapter(
        onClick = { openDetail(it) },
        onLongClick = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.title = getString(R.string.title_history)
        // 挂成 ActionBar，右上角「清空历史」菜单才会显示（主题是 NoActionBar，不挂不显示）
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        repo.observeHistory().observe(this) { list ->
            adapter.submit(list.map {
                val timeText = if (it.positionMs > 0) " · ${fmtTime(it.positionMs)}" else ""
                LocalItem(it.vodId, it.siteKey, it.vodName, it.vodPic,
                    subtitle = "看到第${it.episodeIndex + 1}集$timeText",
                    siteLabel = it.siteKey, id = it.id)
            })
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun fmtTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.action_clear_history).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            AlertDialog.Builder(this).setMessage(R.string.msg_confirm_delete)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch { repo.clearHistory() }
                }.setNegativeButton(R.string.action_cancel, null).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openDetail(item: LocalItem) {
        startActivity(Intent(this, DetailActivity::class.java).apply {
            putExtra("siteKey", item.siteKey)
            putExtra("vodId", item.vodId)
            putExtra("vodName", item.vodName)
        })
    }

    private fun confirmDelete(item: LocalItem) {
        AlertDialog.Builder(this).setMessage(R.string.msg_confirm_delete)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch { repo.deleteHistory(item.id) }
            }.setNegativeButton(R.string.action_cancel, null).show()
    }
}
