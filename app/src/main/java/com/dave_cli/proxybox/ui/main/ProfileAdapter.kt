package com.dave_cli.proxybox.ui.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.databinding.ItemProfileBinding

class ProfileAdapter(
    private val onSelect: (ProfileEntity) -> Unit,
    private val onDelete: (ProfileEntity) -> Unit
) : ListAdapter<ProfileEntity, ProfileAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemProfileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProfileEntity) {
            binding.tvName.text = item.name
            binding.tvProtocol.text = item.protocol.uppercase()
            binding.root.isSelected = item.isSelected
            binding.ivSelected.setImageResource(
                if (item.isSelected) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked
            )

            if (item.latencyMs > 0) {
                binding.tvLatency.visibility = View.VISIBLE
                binding.tvLatency.text = "${item.latencyMs}ms"
                binding.tvLatency.setTextColor(
                    when {
                        item.latencyMs < 200 -> Color.parseColor("#4ADE80")
                        item.latencyMs < 500 -> Color.parseColor("#FACC15")
                        else -> Color.parseColor("#F87171")
                    }
                )
            } else {
                binding.tvLatency.visibility = View.GONE
            }

            binding.root.setOnClickListener { onSelect(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProfileEntity>() {
            override fun areItemsTheSame(a: ProfileEntity, b: ProfileEntity) = a.id == b.id
            override fun areContentsTheSame(a: ProfileEntity, b: ProfileEntity) = a == b
        }
    }
}
