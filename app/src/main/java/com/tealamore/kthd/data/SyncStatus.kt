package com.tealamore.kthd.data

enum class SyncStatus {
    PENDING,
    SYNCED,
    /** Sending failed with a retryable error (network failure, timeout or 5xx); will be retried. */
    FAILED,
    /** The backend rejected the request with a non-retryable error (e.g. 4xx); never retried. */
    REJECTED
}
