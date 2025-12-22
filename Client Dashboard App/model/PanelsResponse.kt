package model

data class PanelsResponse(
    val total_panels: Int,
    val panels: List<PanelData>
)

data class PanelData(
    val esp_id: String,
    val timestamp: String,
    val voltage: Float,
    val current: Float,
    val power: Float,
    val relay_status: String
)