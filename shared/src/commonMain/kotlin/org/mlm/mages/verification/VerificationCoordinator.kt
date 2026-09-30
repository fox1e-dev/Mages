package org.mlm.mages.verification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mlm.mages.MatrixService
import org.mlm.mages.matrix.SasPhase
import org.mlm.mages.matrix.MatrixPort
import org.mlm.mages.matrix.VerifEvent
import org.mlm.mages.matrix.VerificationService
import org.mlm.mages.matrix.asVerificationService
import org.jetbrains.compose.resources.getString
import mages.shared.generated.resources.Res

data class VerificationUiState(
    val sasFlowId: String? = null,
    val sasPhase: SasPhase? = null,
    val sasOtherUser: String? = null,
    val sasOtherDevice: String? = null,
    val sasEmojis: List<String> = emptyList(),
    val sasError: String? = null,
    val sasIncoming: Boolean = false,
    val sasActionInFlight: Boolean = false,
)

class VerificationCoordinator(
    private val service: MatrixService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(VerificationUiState())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    private var inboxToken: ULong? = null

    private var flowJob: Job? = null

    private var verificationService: VerificationService? = null

    init {
        scope.launch {
            service.isReady.first { it }

            service.activeAccount.collectLatest { account ->
                reset()
                if (account != null) {
                    initVerificationService()
                    startInboxIfPossible()
                }
            }
        }
    }

    private fun initVerificationService() {
        val port = service.portOrNull
        verificationService = port?.asVerificationService()
    }

    private fun reset() {
        inboxToken?.let { token ->
            runCatching { service.portOrNull?.stopVerificationInbox(token) }
        }
        inboxToken = null
        flowJob?.cancel()
        flowJob = null
        _state.value = VerificationUiState()
    }

    private fun startFlow(previous: VerificationUiState, block: suspend CoroutineScope.() -> Unit) {
        flowJob?.cancel()
        flowJob = scope.launch {
            previous.sasFlowId?.let { flowId ->
                runCatching { verificationService?.cancelVerification(flowId, previous.sasOtherUser) }
            }
            block()
        }
    }

    private suspend fun startInboxIfPossible() {
        val port = service.portOrNull ?: return
        if (!service.isLoggedInSuspend()) return

        inboxToken = port.startVerificationInbox(object : MatrixPort.VerificationInboxObserver {
            override fun onRequest(flowId: String, fromUser: String, fromDevice: String) {
                flowJob?.cancel()
                flowJob = null
                _state.value = _state.value.copy(
                    sasFlowId = flowId,
                    sasPhase = SasPhase.Requested,
                    sasOtherUser = fromUser,
                    sasOtherDevice = fromDevice,
                    sasEmojis = emptyList(),
                    sasError = null,
                    sasIncoming = true,
                    sasActionInFlight = false
                )
            }

            override fun onError(message: String) {
                _state.value = _state.value.copy(sasError = "Verification inbox: $message")
            }
        })
    }

    fun startSelfVerify(deviceId: String) {
        val previous = _state.value
        _state.value = previous.copy(
            sasFlowId = null,
            sasPhase = SasPhase.Requested,
            sasIncoming = false,
            sasOtherUser = runCatching { service.portOrNull?.whoami() }.getOrNull(),
            sasOtherDevice = deviceId,
            sasError = null
        )

        startFlow(previous) {
            try {
                verificationService?.startDeviceVerification(deviceId)?.collect { event ->
                    handleVerifEvent(event)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Failed,
                    sasError = e.message ?: getString(Res.string.verification_failed_to_start),
                    sasActionInFlight = false
                )
            } ?: run {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Failed,
                    sasError = getString(Res.string.verification_service_unavailable)
                )
            }
        }
    }

    fun startUserVerify(userId: String) {
        val previous = _state.value
        _state.value = previous.copy(
            sasFlowId = null,
            sasPhase = SasPhase.Requested,
            sasIncoming = false,
            sasOtherUser = userId,
            sasError = null
        )

        startFlow(previous) {
            try {
                verificationService?.startUserVerification(userId)?.collect { event ->
                    handleVerifEvent(event)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Failed,
                    sasError = e.message ?: getString(Res.string.verification_failed_to_start),
                    sasActionInFlight = false
                )
            } ?: run {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Failed,
                    sasError = getString(Res.string.verification_service_unavailable)
                )
            }
        }
    }

    private fun handleVerifEvent(event: VerifEvent) {
        when (event) {
            is VerifEvent.Requested -> {
                _state.value = _state.value.copy(
                    sasFlowId = event.flow_id,
                    sasPhase = SasPhase.Requested,
                    sasError = null,
                    sasActionInFlight = false
                )
            }
            is VerifEvent.Ready -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Ready,
                    sasError = null,
                    sasActionInFlight = false
                )
            }
            is VerifEvent.SasStarted -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Started,
                    sasError = null
                )
            }
            is VerifEvent.KeysExchanged -> {
                _state.value = _state.value.copy(
                    sasOtherUser = event.other_user,
                    sasOtherDevice = event.other_device,
                    sasEmojis = event.emojis.map { it.symbol },
                    sasPhase = SasPhase.Emojis,
                    sasError = null
                )
            }
            is VerifEvent.Confirmed -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Confirmed,
                    sasError = null
                )
            }
            is VerifEvent.Done -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Done,
                    sasError = null
                )
                _state.value = VerificationUiState()
            }
            is VerifEvent.Cancelled -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Cancelled,
                    sasError = event.reason
                )
                _state.value = VerificationUiState()
            }
            is VerifEvent.Error -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Failed,
                    sasError = event.message,
                    sasActionInFlight = false
                )
            }
        }
    }

    fun accept() {
        val previous = _state.value
        val flowId = previous.sasFlowId
        if (flowId == null || previous.sasPhase != SasPhase.Requested) return

        val otherUser = previous.sasOtherUser
        _state.value = previous.copy(sasActionInFlight = true, sasError = null)

        flowJob?.cancel()
        flowJob = scope.launch {
            try {
                verificationService
                    ?.acceptAndObserveVerification(flowId, otherUser ?: "")
                    ?.collect { event -> handleVerifEvent(event) }
                    ?: run {
                        _state.value = _state.value.copy(
                            sasActionInFlight = false,
                            sasError = getString(Res.string.verification_service_unavailable)
                        )
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Failed,
                    sasError = e.message ?: getString(Res.string.accept_failed),
                    sasActionInFlight = false
                )
            }
        }
    }

    fun confirm() {
        val flowId = _state.value.sasFlowId ?: return
        val otherUser = _state.value.sasOtherUser

        scope.launch {
            val ok = verificationService?.confirmSas(flowId, otherUser) ?: false
            if (!ok) _state.value = _state.value.copy(sasError = getString(Res.string.confirm_failed))
        }
    }

    fun cancel() {
        val flowId = _state.value.sasFlowId ?: return
        val otherUser = _state.value.sasOtherUser

        scope.launch {
            val ok = verificationService?.cancelVerification(flowId, otherUser) ?: false
            if (!ok) {
                _state.value = _state.value.copy(sasError = getString(Res.string.cancel_failed))
            } else {
                flowJob?.cancel()
                flowJob = null
                _state.value = VerificationUiState()
            }
        }
    }
}