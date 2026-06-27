package dev.komrd.core.datastore

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "KomrdDataStore"

internal fun <T> Flow<T>.catchDataStoreError(default: T): Flow<T> =
    catch { error ->
        Log.w(TAG, "DataStore read failed, falling back to default.", error)
        emit(default)
    }

internal fun sanitize(
    name: String,
    value: Int,
    min: Int,
    max: Int,
): Int =
    when {
        value < min -> {
            Log.w(TAG, "$name=$value below min $min, coerced to $min.")
            min
        }
        value > max -> {
            Log.w(TAG, "$name=$value above max $max, coerced to $max.")
            max
        }
        else -> value
    }

/** [sanitize]のLong版([PrefetchSettingsStore.maxBytes]用)。上限は Long.MAX_VALUE で実質無上限。 */
internal fun sanitize(
    name: String,
    value: Long,
    min: Long,
    max: Long,
): Long =
    when {
        value < min -> {
            Log.w(TAG, "$name=$value below min $min, coerced to $min.")
            min
        }
        value > max -> {
            Log.w(TAG, "$name=$value above max $max, coerced to $max.")
            max
        }
        else -> value
    }
