package com.dave_cli.proxybox.ui.tv

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.data.db.ProfileEntity

class TvProfileAdapter(
    context: Context,
    private val onClick: (ProfileEntity) -> Unit
) : ArrayAdapter<ProfileEntity>(context, R.layout.item_tv_profile) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_tv_profile, parent, false)
        val item = getItem(position) ?: return view

        view.findViewById<TextView>(R.id.tvName).text = item.name
        view.findViewById<TextView>(R.id.tvProtocol).text = item.protocol.uppercase()

        val tvLatency = view.findViewById<TextView>(R.id.tvLatency)
        if (item.latencyMs > 0) {
            tvLatency.visibility = View.VISIBLE
            tvLatency.text = "${item.latencyMs}ms"
            tvLatency.setTextColor(
                when {
                    item.latencyMs < 200 -> Color.parseColor("#4ADE80")
                    item.latencyMs < 500 -> Color.parseColor("#FACC15")
                    else -> Color.parseColor("#F87171")
                }
            )
        } else {
            tvLatency.visibility = View.GONE
        }

        val indicator = view.findViewById<View>(R.id.viewSelectedIndicator)
        val badge = view.findViewById<TextView>(R.id.tvSelectedBadge)
        val root = view.findViewById<View>(R.id.profileRoot)

        if (item.isSelected) {
            indicator.visibility = View.VISIBLE
            badge.visibility = View.VISIBLE
            root.setBackgroundColor(Color.parseColor("#1A7C6FFF"))
        } else {
            indicator.visibility = View.GONE
            badge.visibility = View.GONE
            root.setBackgroundColor(Color.TRANSPARENT)
        }

        view.setOnClickListener { onClick(item) }
        return view
    }
}
