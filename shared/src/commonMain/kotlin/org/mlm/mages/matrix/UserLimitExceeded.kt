package org.mlm.mages.matrix

/** Display text of Rust `FfiError::UserLimitExceeded`, the only form it takes over wasm. */
const val USER_LIMIT_EXCEEDED_MESSAGE =
    "The homeserver reported that an account limit was exceeded"

class UserLimitExceededException : IllegalStateException(USER_LIMIT_EXCEEDED_MESSAGE)

fun Throwable.isUserLimitExceeded(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is UserLimitExceededException) return true
        current = current.cause
    }
    return false
}
