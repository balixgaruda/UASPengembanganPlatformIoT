package network

import model.MonitoringResponse
import model.RelayRequest
import model.RelayResponse
import model.RelayStatusResponse
import model.SensorDataPoint
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {

    @GET("monitoring")
    suspend fun getMonitoring(
        @Query("esp_id") espId: String
    ): MonitoringResponse


    @POST("relay")
    suspend fun controlRelay(
        @Body request: RelayRequest
    ): RelayResponse

    @GET("relay/status/{relay_id}")
    suspend fun getRelayStatus(
        @Path("relay_id") relayId: String
    ): RelayStatusResponse
}
