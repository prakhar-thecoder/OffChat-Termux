package com.appholaworld.offchat.adapters

import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.termux.databinding.ItemWifiNetworkBinding
import com.termux.R

class WifiNetworkAdapter(
    private var items: List<ScanResult> = emptyList()
) : RecyclerView.Adapter<WifiNetworkAdapter.WifiViewHolder>() {

    class WifiViewHolder(val binding: ItemWifiNetworkBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val binding = ItemWifiNetworkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WifiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        val network = items[position]
        holder.binding.apply {
            tvSsid.text = if (network.SSID.isNullOrEmpty()) "Hidden Network" else network.SSID
            tvBssid.text = "BSSID: ${network.BSSID}"
            tvSignalStrength.text = "Signal: ${network.level} dBm"
            
            // Choose signal icon based on level
            val signalIcon = when {
                network.level > -60 -> R.drawable.outline_signal_wifi_4_bar_24
                network.level > -70 -> R.drawable.outline_signal_wifi_2_bar_24
                network.level > -85 -> R.drawable.outline_signal_wifi_1_bar_24
                else -> R.drawable.outline_signal_wifi_0_bar_24
            }
            ivWifiIcon.setImageResource(signalIcon)
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<ScanResult>) {
        val oldItems = items
        items = newList
        
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldItems[oldPos].BSSID == newList[newPos].BSSID
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldItems[oldPos].level == newList[newPos].level &&
                        oldItems[oldPos].SSID == newList[newPos].SSID
            }
        }).dispatchUpdatesTo(this)
    }
}
