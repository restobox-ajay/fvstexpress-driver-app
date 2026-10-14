package com.fvstexpress.driver.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fvstexpress.driver.data.model.LeftBehind
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.data.model.OrderDetail
import com.fvstexpress.driver.data.model.PickedUpResponse
import com.fvstexpress.driver.data.model.Status
import com.fvstexpress.driver.data.model.StopDetail
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.data.repo.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LocalPhoto(val file: File, val id: String? = null, val uploading: Boolean = true, val error: Boolean = false)

data class StopState(
    val detail: StopDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** Pickup checklist: references the driver ticked. */
    val checked: Set<String> = emptySet(),
    val pickupResult: PickedUpResponse? = null,
    val orderInfo: OrderDetail? = null,
    // Proof of delivery form
    val photos: List<LocalPhoto> = emptyList(),
    val leftAt: String = "",
    val customerContact: String = "not_needed",
    val recipientName: String = "",
) {
    val isPickup get() = detail?.leg == Leg.PICKUP
    val isCompleted get() = detail?.let {
        (it.leg == Leg.PICKUP && it.status in listOf(Status.PICKED_UP, Status.IN_TRANSIT, Status.DELIVERED)) ||
            it.status == Status.DELIVERED || it.status == Status.FAILED || it.status == Status.CANCELLED
    } ?: false
    val canDeliver get() = photos.any { it.id != null } && photos.none { it.uploading } && leftAt.isNotBlank()
}

class StopViewModel(private val repo: DriverRepository, private val key: String) : ViewModel() {
    private val _state = MutableStateFlow(StopState())
    val state: StateFlow<StopState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.detail == null, error = null) }
            when (val r = repo.stop(key)) {
                is ApiResult.Ok -> _state.update { it.copy(detail = r.data, loading = false) }
                is ApiResult.Err -> _state.update { it.copy(error = r.message, loading = false) }
                ApiResult.Queued -> Unit
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Shows the change straight away when there's no signal; the server catches up on sync. */
    private fun applyLocal(status: String) {
        _state.update { s ->
            s.copy(detail = s.detail?.copy(status = status, canUndo = false, nextAllowed = emptyList()), message = "No signal — saved on this phone. It'll send when you're back online.")
        }
    }

    private fun <T> handle(r: ApiResult<T>, localStatus: String?, onOk: (T) -> Unit = {}) {
        when (r) {
            is ApiResult.Ok -> { onOk(r.data); load() }
            is ApiResult.Err -> { _state.update { it.copy(message = r.message) }; if (r.code == 409) load() }
            ApiResult.Queued -> localStatus?.let { applyLocal(it) }
        }
        _state.update { it.copy(busy = false) }
    }

    fun markPickingUp() = run(Status.PICKING_UP) { handle(repo.pickingUp(key), Status.PICKING_UP) }

    fun toggleChecked(reference: String) = _state.update {
        it.copy(checked = if (reference in it.checked) it.checked - reference else it.checked + reference)
    }

    fun markPickedUp(leftBehind: Map<String, String>, note: String) = run(Status.PICKED_UP) {
        val picked = _state.value.checked.toList()
        val lb = leftBehind.map { (ref, action) -> LeftBehind(ref, action) }
        handle(repo.pickedUp(key, picked, lb, note), Status.PICKED_UP) { res -> _state.update { it.copy(pickupResult = res) } }
    }

    fun markInTransit() = run(Status.IN_TRANSIT) { handle(repo.inTransit(key), Status.IN_TRANSIT) }

    fun undo() = run(null) {
        val current = _state.value.detail?.status ?: return@run
        handle(repo.undo(key, current), null) { _state.update { it.copy(pickupResult = null) } }
    }

    fun addPhoto(file: File) {
        val reference = _state.value.detail?.orders?.firstOrNull()?.reference ?: return
        _state.update { it.copy(photos = it.photos + LocalPhoto(file)) }
        viewModelScope.launch {
            val r = repo.uploadPhoto(reference, file)
            _state.update { s ->
                s.copy(
                    photos = s.photos.map { p ->
                        if (p.file != file) p
                        else when (r) {
                            is ApiResult.Ok -> p.copy(id = r.data, uploading = false)
                            else -> p.copy(uploading = false, error = true)
                        }
                    },
                    message = (r as? ApiResult.Err)?.message ?: s.message,
                )
            }
        }
    }

    fun removePhoto(photo: LocalPhoto) {
        _state.update { it.copy(photos = it.photos - photo) }
        viewModelScope.launch { photo.id?.let { repo.deletePhoto(it) }; photo.file.delete() }
    }

    fun setLeftAt(v: String) = _state.update { it.copy(leftAt = v) }
    fun setContact(v: String) = _state.update { it.copy(customerContact = v) }
    fun setRecipient(v: String) = _state.update { it.copy(recipientName = v) }

    fun markDelivered() = run(Status.DELIVERED) {
        val s = _state.value
        val reference = s.detail?.orders?.firstOrNull()?.reference ?: return@run
        val ids = s.photos.mapNotNull { it.id }
        handle(repo.delivered(key, reference, ids, s.leftAt, s.customerContact, s.recipientName), Status.DELIVERED)
    }

    fun showOrder(reference: String) {
        viewModelScope.launch {
            when (val r = repo.order(reference)) {
                is ApiResult.Ok -> _state.update { it.copy(orderInfo = r.data) }
                is ApiResult.Err -> _state.update { it.copy(message = r.message) }
                ApiResult.Queued -> Unit
            }
        }
    }

    fun hideOrder() = _state.update { it.copy(orderInfo = null) }

    private fun run(@Suppress("UNUSED_PARAMETER") target: String?, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }
}
