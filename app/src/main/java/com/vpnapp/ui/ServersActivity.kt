package com.vpnapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vpnapp.R
import com.vpnapp.databinding.ActivityServersBinding
import com.vpnapp.model.VpnServer

class ServersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServersBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ServersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Серверы"

        adapter = ServersAdapter { server ->
            viewModel.selectServer(server)
            Toast.makeText(this, "Выбран: ${server.name}", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.recyclerServers.layoutManager = LinearLayoutManager(this)
        binding.recyclerServers.adapter = adapter

        viewModel.servers.observe(this) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnUpdate.setOnClickListener {
            viewModel.fetchServers()
            Toast.makeText(this, "Загрузка серверов…", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

class ServersAdapter(
    private val onSelect: (VpnServer) -> Unit
) : RecyclerView.Adapter<ServersAdapter.VH>() {

    private var list: List<VpnServer> = emptyList()

    fun submitList(newList: List<VpnServer>) {
        list = newList
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvFlag: TextView = view.findViewById(R.id.tv_flag)
        val tvName: TextView = view.findViewById(R.id.tv_server_name)
        val tvCountry: TextView = view.findViewById(R.id.tv_country)
        val tvLoad: TextView = view.findViewById(R.id.tv_load)
        val tvProtocol: TextView = view.findViewById(R.id.tv_protocol)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val server = list[position]
        holder.tvFlag.text = server.flag
        holder.tvName.text = server.name
        holder.tvCountry.text = server.country
        holder.tvLoad.text = "Нагрузка: ${server.load}%"
        holder.tvProtocol.text = server.protocol.uppercase()
        holder.itemView.setOnClickListener { onSelect(server) }
    }

    override fun getItemCount() = list.size
}
