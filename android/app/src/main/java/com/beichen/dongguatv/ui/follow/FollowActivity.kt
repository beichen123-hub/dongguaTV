package com.beichen.dongguatv.ui.follow

import android.content.Intent
import android.os.Bundle
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

class FollowActivity : AppCompatActivity() {

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
        binding.toolbar.title = getString(R.string.title_follows)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        repo.observeFollows().observe(this) { list ->
            adapter.submit(list.map {
                LocalItem(it.vodId, it.siteKey, it.vodName, it.vodPic,
                    subtitle = "已追 ${it.lastEpisode} 集",
                    siteLabel = it.siteKey, id = it.id)
            })
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
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
                lifecycleScope.launch { repo.deleteFollow(item.id) }
            }.setNegativeButton(R.string.action_cancel, null).show()
    }
}
