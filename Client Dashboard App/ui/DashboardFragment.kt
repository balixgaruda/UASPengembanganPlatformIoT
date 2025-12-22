package ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.uasplatform.R
import kotlinx.coroutines.launch
import model.RelayRequest
import network.ApiClient

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private lateinit var txtPower: TextView
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtPower = view.findViewById(R.id.txtPower)
        btnOn = view.findViewById(R.id.btnOn)
        btnOff = view.findViewById(R.id.btnOff)

        loadMonitoring()

        btnOn.setOnClickListener { sendRelayCommand("ON") }
        btnOff.setOnClickListener { sendRelayCommand("OFF") }
    }

    private fun loadMonitoring() {
        lifecycleScope.launch {
            try {
                val data = ApiClient.apiService.getMonitoring("ESP32-01")
                txtPower.text = "Power: ${data.power} W"
            } catch (_: Exception) {
                txtPower.text = "Server Error"
            }
        }
    }

    private fun sendRelayCommand(command: String) {
        lifecycleScope.launch {
            ApiClient.apiService.controlRelay(
                RelayRequest(
                    relay_id = "Relay-1",
                    command = command,
                    reason = "User Control",
                    initiated_by = "mobile"
                )
            )
        }
    }
}
