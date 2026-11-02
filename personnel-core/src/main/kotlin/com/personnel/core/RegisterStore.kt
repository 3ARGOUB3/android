package com.personnel.core

import kotlinx.serialization.json.Json
import java.io.File

/** Saves each register as a JSON file in [dir]. */
class RegisterStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The default registers in their fixed order, with saved data when there is some. */
    fun loadAll(): List<Register> = DEFAULT_REGISTERS.map { (id, title) ->
        val file = fileOf(id)
        val saved = if (file.exists()) {
            runCatching { json.decodeFromString(Register.serializer(), file.readText()) }.getOrNull()
        } else {
            null
        }
        saved?.copy(id = id, title = title) ?: Register(id, title)
    }

    fun save(register: Register) {
        dir.mkdirs()
        val file = fileOf(register.id)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(Register.serializer(), register))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun fileOf(id: String) = File(dir, "$id.json")
}
