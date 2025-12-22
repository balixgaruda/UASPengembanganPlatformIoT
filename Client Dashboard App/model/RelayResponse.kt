package model

data class RelayResponse(
    val message: String,
    val relay_id: String,
    val command: String,
    val status: String
)
