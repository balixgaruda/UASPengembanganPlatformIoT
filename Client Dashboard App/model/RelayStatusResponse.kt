package model

data class RelayStatusResponse(
    val relay_id: String,
    val status: String,
    val last_update: String
)
