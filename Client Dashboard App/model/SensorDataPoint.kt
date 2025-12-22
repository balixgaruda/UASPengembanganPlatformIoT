package model

data class SensorDataPoint(
    val timestamp: Long,
    val voltage: Double,
    val current: Double,
    val power: Double
)