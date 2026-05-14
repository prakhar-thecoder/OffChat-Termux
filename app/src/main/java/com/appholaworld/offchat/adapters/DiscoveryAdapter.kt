package com.appholaworld.offchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.termux.databinding.ItemNearbyDeviceBinding
import com.appholaworld.offchat.models.ConnectionStatus
import com.appholaworld.offchat.models.NearbyDevice
import com.termux.R

class DiscoveryAdapter(
    private var items: List<NearbyDevice>,
    private val onConnectClick: (NearbyDevice) -> Unit,
    private val onUnlinkClick: (NearbyDevice) -> Unit
) : RecyclerView.Adapter<DiscoveryAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(val binding: ItemNearbyDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemNearbyDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = items[position]
        holder.binding.apply {
            tvDeviceName.text = device.userName ?: device.name
            tvDeviceType.text = "${device.type.name} • Available"
            tvIpAddress.text = device.ipAddress ?: "IP Unknown"
            ivStatusIndicator.visibility = if (device.status == ConnectionStatus.CONNECTED) View.VISIBLE else View.GONE
            
            btnConnect.text = when(device.status) {
                ConnectionStatus.CONNECTED -> "Unlink"
                ConnectionStatus.CONNECTING -> "Connecting..."
                else -> "Connect"
            }

            btnConnect.setOnClickListener {
                if (device.status == ConnectionStatus.CONNECTED) {
                    onUnlinkClick(device)
                } else {
                    onConnectClick(device)
                }
            }
            
            root.setOnClickListener { onConnectClick(device) }

            // Real Signal Strength indicator
            val signalIcon = when {
                device.rssi > -60 -> R.drawable.outline_signal_wifi_4_bar_24
                device.rssi > -70 -> R.drawable.outline_signal_wifi_2_bar_24
                device.rssi > -85 -> R.drawable.outline_signal_wifi_1_bar_24
                else -> R.drawable.outline_signal_wifi_0_bar_24
            }
            ivSignal.setImageResource(signalIcon)
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<NearbyDevice>) {
        val oldItems = items
        items = newList
        
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldItems[oldPos].id == newList[newPos].id
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldItems[oldPos] == newList[newPos]
        }).dispatchUpdatesTo(this)
    }
}
