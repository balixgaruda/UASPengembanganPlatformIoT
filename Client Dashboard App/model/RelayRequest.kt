package model

data class RelayRequest(
    val relay_id: String,
    val command: String,
    val reason: String,
    val initiated_by: String
)