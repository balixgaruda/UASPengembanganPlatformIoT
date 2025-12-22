package model

data class MonitoringResponse(
    val esp_id: String,
    val timestamp: String,
    val voltage: Double,
    val current: Double,
    val power: Double
)
