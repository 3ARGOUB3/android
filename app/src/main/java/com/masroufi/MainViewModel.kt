package com.masroufi

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.core.ImportException
import com.masroufi.core.Transaction
import com.masroufi.core.TransactionStore
import com.masroufi.core.XlsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val transactions: List<Transaction> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = TransactionStore(File(application.filesDir, "transactions.json"))
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            _state.update { it.copy(transactions = saved, loading = false) }
        }
    }

    /** Replaces all saved transactions with the contents of the picked .xls file. */
    fun import(uri: Uri) {
        val app = getApplication<Application>()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = app.contentResolver.openInputStream(uri)?.use(XlsImporter::parse)
                        ?: throw ImportException("Cannot open the file")
                    store.save(parsed.transactions)
                    parsed
                }
            }
            result.fold(
                onSuccess = { parsed ->
                    _state.update {
                        it.copy(
                            transactions = parsed.transactions,
                            loading = false,
                            message = app.getString(
                                R.string.imported,
                                parsed.transactions.size,
                                parsed.skippedRows.size,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            message = app.getString(R.string.import_failed, error.message ?: error.javaClass.simpleName),
                        )
                    }
                },
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
