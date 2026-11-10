package com.beichen.dongguatv.ui.mine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.databinding.FragmentMineBinding

class MineFragment : Fragment() {

    private var _binding: FragmentMineBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = Repository.get(requireContext())
        repo.observeFavorites().observe(viewLifecycleOwner) {
            binding.tvFavCount.text = it.size.toString()
        }
        repo.observeHistory().observe(viewLifecycleOwner) {
            binding.tvHisCount.text = it.size.toString()
        }
        repo.observeFollows().observe(viewLifecycleOwner) {
            binding.tvFollowCount.text = "${it.size} 部"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
