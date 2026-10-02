package org.mlm.mages.verification

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
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
import mages.shared.generated.resources.*
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

    @Volatile
    private var flowEpoch: Long = 0

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

    private fun killFlowJob() {
        flowJob?.cancel()
        flowJob = null
        flowEpoch++
    }

    private fun reset() {
        inboxToken?.let { token ->
            runCatching { service.portOrNull?.stopVerificationInbox(token) }
        }
        inboxToken = null
        killFlowJob()
        _state.value = VerificationUiState()
    }

    private fun startFlow(previous: VerificationUiState, block: suspend CoroutineScope.(Long) -> Unit) {
        val epoch = flowEpoch
        flowJob = scope.launch {
            previous.sasFlowId?.let { flowId ->
                runCatching { verificationService?.cancelVerification(flowId, previous.sasOtherUser) }
            }
            block(epoch)
        }
    }

    private suspend fun startInboxIfPossible() {
        val port = service.portOrNull ?: return
        if (!service.isLoggedInSuspend()) return

        inboxToken = port.startVerificationInbox(object : MatrixPort.VerificationInboxObserver {
            override fun onRequest(flowId: String, fromUser: String, fromDevice: String) {
                val current = _state.value
                val phase = current.sasPhase
                val live = phase != null && phase != SasPhase.Done &&
                    phase != SasPhase.Cancelled && phase != SasPhase.Failed
                if (live) {
                    if (current.sasFlowId == flowId) return
                    if (phase == SasPhase.Emojis || phase == SasPhase.Confirmed) return
                    val me = runCatching { service.portOrNull?.whoami() }.getOrNull()
                    val keepOurs = when {
                        me == null -> true
                        me != fromUser -> me < fromUser
                        else -> {
                            val ownFlow = current.sasFlowId
                            ownFlow == null || ownFlow > flowId
                        }
                    }
                    if (keepOurs) return
                    current.sasFlowId?.let { activeId ->
                        val activeUser = current.sasOtherUser
                        scope.launch {
                            runCatching { verificationService?.cancelVerification(activeId, activeUser) }
                        }
                    }
                }
                killFlowJob()
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
                scope.launch {
                    _state.value = _state.value.copy(sasError = getString(Res.string.verification_inbox_error, message))
                }
            }
        })
    }

    fun startSelfVerify(deviceId: String) {
        killFlowJob()
        val previous = _state.value
        _state.value = previous.copy(
            sasFlowId = null,
            sasPhase = SasPhase.Requested,
            sasIncoming = false,
            sasOtherUser = runCatching { service.portOrNull?.whoami() }.getOrNull(),
            sasOtherDevice = deviceId,
            sasError = null
        )

        startFlow(previous) { epoch ->
            try {
                verificationService?.startDeviceVerification(deviceId)?.collect { event ->
                    handleVerifEvent(epoch, event)
                } ?: run {
                    if (epoch == flowEpoch) {
                        _state.value = _state.value.copy(
                            sasPhase = SasPhase.Failed,
                            sasError = getString(Res.string.verification_service_unavailable)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (epoch == flowEpoch) {
                    _state.value = _state.value.copy(
                        sasPhase = SasPhase.Failed,
                        sasError = e.message ?: getString(Res.string.verification_failed_to_start),
                        sasActionInFlight = false
                    )
                }
            }
        }
    }

    fun startUserVerify(userId: String) {
        killFlowJob()
        val previous = _state.value
        _state.value = previous.copy(
            sasFlowId = null,
            sasPhase = SasPhase.Requested,
            sasIncoming = false,
            sasOtherUser = userId,
            sasError = null
        )

        startFlow(previous) { epoch ->
            try {
                verificationService?.startUserVerification(userId)?.collect { event ->
                    handleVerifEvent(epoch, event)
                } ?: run {
                    if (epoch == flowEpoch) {
                        _state.value = _state.value.copy(
                            sasPhase = SasPhase.Failed,
                            sasError = getString(Res.string.verification_service_unavailable)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (epoch == flowEpoch) {
                    _state.value = _state.value.copy(
                        sasPhase = SasPhase.Failed,
                        sasError = e.message ?: getString(Res.string.verification_failed_to_start),
                        sasActionInFlight = false
                    )
                }
            }
        }
    }

    private fun handleVerifEvent(epoch: Long, event: VerifEvent) {
        if (epoch != flowEpoch) return
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
                    sasError = null,
                    sasActionInFlight = false
                )
            }
            is VerifEvent.Cancelled -> {
                _state.value = _state.value.copy(
                    sasPhase = SasPhase.Cancelled,
                    sasError = event.reason,
                    sasActionInFlight = false
                )
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
        killFlowJob()
        val epoch = flowEpoch
        _state.value = previous.copy(sasActionInFlight = true, sasError = null)

        flowJob = scope.launch {
            try {
                verificationService
                    ?.acceptAndObserveVerification(flowId, otherUser ?: "")
                    ?.collect { event -> handleVerifEvent(epoch, event) }
                    ?: run {
                        if (epoch == flowEpoch) {
                            _state.value = _state.value.copy(
                                sasActionInFlight = false,
                                sasError = getString(Res.string.verification_service_unavailable)
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (epoch == flowEpoch) {
                    _state.value = _state.value.copy(
                        sasPhase = SasPhase.Failed,
                        sasError = e.message ?: getString(Res.string.accept_failed),
                        sasActionInFlight = false
                    )
                }
            }
        }
    }

    fun confirm() {
        val flowId = _state.value.sasFlowId ?: return
        val otherUser = _state.value.sasOtherUser
        val epoch = flowEpoch

        scope.launch {
            val ok = verificationService?.confirmSas(flowId, otherUser) ?: false
            if (!ok && epoch == flowEpoch) {
                _state.value = _state.value.copy(sasError = getString(Res.string.confirm_failed))
            }
        }
    }

    fun cancel() {
        val current = _state.value
        val flowId = current.sasFlowId
        val otherUser = current.sasOtherUser
        val phase = current.sasPhase
        val terminal =
            phase == SasPhase.Done || phase == SasPhase.Cancelled || phase == SasPhase.Failed

        if (flowId != null && !terminal) {
            killFlowJob()
            val epoch = flowEpoch
            scope.launch {
                val ok = verificationService?.cancelVerification(flowId, otherUser) ?: false
                if (epoch != flowEpoch) return@launch
                if (ok) {
                    _state.value = VerificationUiState()
                } else {
                    _state.value = _state.value.copy(sasError = getString(Res.string.cancel_failed))
                }
            }
            return
        }

        if (phase == SasPhase.Failed && flowId != null) {
            scope.launch {
                runCatching { verificationService?.cancelVerification(flowId, otherUser) }
            }
        }
        killFlowJob()
        _state.value = VerificationUiState()
    }
}