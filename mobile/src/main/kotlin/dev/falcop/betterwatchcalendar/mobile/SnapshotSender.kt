package dev.falcop.betterwatchcalendar.mobile

import arrow.core.Either
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import dev.falcop.betterwatchcalendar.shared.ScheduleProtocol
import dev.falcop.betterwatchcalendar.shared.ScheduleSnapshot
import dev.falcop.betterwatchcalendar.shared.encode
import kotlinx.coroutines.tasks.await

data class SendError(val message: String)

/**
 * Overwrites the snapshot DataItem. Returns the payload size in bytes. Play Services delivers it to
 * the watch whenever the two are connected, so success here does not mean the watch has it yet.
 */
suspend fun sendSnapshot(
  dataClient: DataClient,
  snapshot: ScheduleSnapshot,
): Either<SendError, Int> {
  val payload = snapshot.encode()
  val request = PutDataRequest.create(ScheduleProtocol.PATH).setData(payload).setUrgent()
  return Either.catchOrThrow<ApiException, _> { dataClient.putDataItem(request).await() }
    .map { payload.size }
    .mapLeft { SendError("Data Layer: ${it.statusCode} ${it.message}") }
}
