package com.example.allinoneprint

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.io.path.name

class DeviceRecyclerAdapter(private var devices: List<datacalss>, private val onItemClicked: (datacalss) -> Unit) : RecyclerView.Adapter<DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device, parent, false) // Your row layout file
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onItemClicked)
    }

    override fun getItemCount(): Int = devices.size

    // Optional: If you need to update the list later
    @SuppressLint("NotifyDataSetChanged") // Use more specific notify methods if possible
    fun updateData(newDevices: List<datacalss>) {
        devices = newDevices
        notifyDataSetChanged() // Or more specific notify methods
    }
}
class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val nameTextView: TextView = itemView.findViewById(R.id.textView2) // Your TextView ID

    fun bind(deviceInfo: datacalss, clickListener: (datacalss) -> Unit) {
        nameTextView.text = deviceInfo.shubh
        itemView.setOnClickListener { clickListener(deviceInfo) }
    }
}