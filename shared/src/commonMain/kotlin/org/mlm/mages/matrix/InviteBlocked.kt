package org.mlm.mages.matrix

/** Display text of Rust `FfiError::InviteBlocked`, the only form it takes over wasm. */
const val INVITE_BLOCKED_MESSAGE = "The invite was blocked"

class InviteBlockedException : IllegalStateException(INVITE_BLOCKED_MESSAGE)

fun Throwable.isInviteBlocked(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is InviteBlockedException) return true
        current = current.cause
    }
    return false
}
