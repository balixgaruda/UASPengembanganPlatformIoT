package ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.uasplatform.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import model.RelayRequest
import model.SensorDataPoint
import network.ApiClient

class MainActivity : AppCompatActivity() {

    private lateinit var txtVoltage: TextView
    private lateinit var txtCurrent: TextView
    private lateinit var txtPower: TextView
    private lateinit var txtRelayStatus: TextView
    private lateinit var txtLastUpdate: TextView
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button
    private lateinit var progressBar: ProgressBar

    // Charts
    private lateinit var chartVoltage: LineChart
    private lateinit var chartCurrent: LineChart
    private lateinit var chartPower: LineChart

    private val handler = Handler(Looper.getMainLooper())
    private var selectedPanel = "ESP32-01"
    private val dataHistory = mutableListOf<SensorDataPoint>()
    private val maxDataPoints = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_dashboard)

        initViews()
        setupCharts()
        startMonitoring()
    }

    private fun initViews() {
        txtVoltage = findViewById(R.id.txtVoltage)
        txtCurrent = findViewById(R.id.txtCurrent)
        txtPower = findViewById(R.id.txtPower)
        txtRelayStatus = findViewById(R.id.txtStatus)
        btnOn = findViewById(R.id.btnOn)
        btnOff = findViewById(R.id.btnOff)

        chartVoltage = findViewById(R.id.chartVoltage)
        chartCurrent = findViewById(R.id.chartCurrent)
        chartPower = findViewById(R.id.chartPower)

        btnOn.setOnClickListener {
            sendRelayCommand("ON")
        }

        btnOff.setOnClickListener {
            sendRelayCommand("OFF")
        }
        val btnEsp1 = findViewById<Button>(R.id.btnEsp1)
        val btnEsp2 = findViewById<Button>(R.id.btnEsp2)
        val btnEsp3 = findViewById<Button>(R.id.btnEsp3)

        btnEsp1.setOnClickListener { switchEsp("ESP32-01") }
        btnEsp2.setOnClickListener { switchEsp("ESP32-02") }
        btnEsp3.setOnClickListener { switchEsp("ESP32-03") }
    }

    private fun setupCharts() {
        setupChart(chartVoltage, "Voltage (V)", Color.parseColor("#FF6B6B"))
        setupChart(chartCurrent, "Current (A)", Color.parseColor("#4ECDC4"))
        setupChart(chartPower, "Power (W)", Color.parseColor("#FFE66D"))
    }

    private fun setupChart(chart: LineChart, label: String, color: Int) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
            }

            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                fetchMonitoringData()
                fetchRelayStatus()
                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun fetchMonitoringData() {
        lifecycleScope.launch {
            try {
                val data = ApiClient.apiService.getMonitoring(selectedPanel)

                val espId = data.esp_id

                val list = espDataMap.getOrPut(espId) {
                    mutableListOf()
                }

                list.add(
                    SensorDataPoint(
                        timestamp = System.currentTimeMillis(),
                        voltage = data.voltage,
                        current = data.current,
                        power = data.power
                    )
                )

                if (list.size > maxDataPoints) {
                    list.removeAt(0)
                }
                if (espId == selectedPanel) {
                    txtVoltage.text = "Voltage: ${data.voltage} V"
                    txtCurrent.text = "Current: ${data.current} A"
                    txtPower.text = "Power: ${data.power} W"

                    updateCharts(list)
                }
                if (selectedPanel == "ESP32-01") {
                    ApiClient.apiService.getMonitoring(selectedPanel)
                } else {
                    ApiClient.apiService.getMonitoring(selectedPanel)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateCharts(data: List<SensorDataPoint>) {

        updateChart(chartVoltage,
            data.mapIndexed { i, it -> Entry(i.toFloat(), it.voltage.toFloat()) },
            "Voltage",  Color.parseColor("#FF6B6B"))

        updateChart(chartCurrent,
            data.mapIndexed { i, it -> Entry(i.toFloat(), it.current.toFloat()) },
            "Current", Color.parseColor("#4ECDC4"))

        updateChart(chartPower,
            data.mapIndexed { i, it -> Entry(i.toFloat(), it.power.toFloat()) },
            "Power", Color.parseColor("#FFE66D"))
     }


    private fun updateChart(chart: LineChart, entries: List<Entry>, label: String, color: Int) {
        if (entries.isEmpty()) return

        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = color
            fillAlpha = 50
        }

        chart.data = LineData(dataSet)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun fetchRelayStatus() {
        lifecycleScope.launch {
            try {
                val relay = ApiClient.apiService.getRelayStatus("Relay-1")

                txtRelayStatus.text = "Relay: ${relay.status}"

                if (relay.status == "ON") {
                    txtRelayStatus.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    txtRelayStatus.setTextColor(Color.parseColor("#F44336"))
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendRelayCommand(command: String) {
        lifecycleScope.launch {
            try {
                val request = RelayRequest(
                    relay_id = "Relay-$selectedPanel",
                    command = command,
                    reason = "MOBILE_CONTROL",
                    initiated_by = "android_app"
                )

                ApiClient.apiService.controlRelay(request)

                Toast.makeText(
                    this@MainActivity,
                    "$selectedPanel relay $command",
                    Toast.LENGTH_SHORT
                ).show()

                fetchMonitoringData()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Relay error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val espDataMap = mutableMapOf<String, MutableList<SensorDataPoint>>()

    private val espList = listOf(
        "ESP32-01",
        "ESP32-02",
        "ESP32-03"
    )


    private fun switchEsp(espId: String) {
        if (selectedPanel == espId) return

        selectedPanel = espId
        dataHistory.clear()

        chartVoltage.clear()
        chartCurrent.clear()
        chartPower.clear()

        txtVoltage.text = "Voltage: -- V"
        txtCurrent.text = "Current: -- A"
        txtPower.text = "Power: -- W"
        txtRelayStatus.text = "Relay: --"

        Toast.makeText(this, "Switched to $espId", Toast.LENGTH_SHORT).show()

        fetchMonitoringData()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

}