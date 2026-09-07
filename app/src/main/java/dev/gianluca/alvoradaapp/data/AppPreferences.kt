package dev.gianluca.alvoradaapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesStore by preferencesDataStore(name = "settings")

/**
 * Preferências que não pertencem ao banco.
 *
 * Fora do Room de propósito: são estado de apresentação, não dados do usuário. Se
 * entrassem no banco, um backup restaurado num aparelho novo carregaria junto o
 * "já vi as boas-vindas" — e as boas-vindas existem justamente para o caso em que o
 * aparelho ainda não foi configurado, que é exatamente o de uma instalação nova.
 */
class AppPreferences(context: Context) {

    private val store = context.applicationContext.preferencesStore

    /** Falso até a tela de boas-vindas ser dispensada uma vez, de qualquer forma. */
    val welcomeSeen: Flow<Boolean> = store.data.map { it[WELCOME_SEEN] ?: false }

    suspend fun markWelcomeSeen() {
        store.edit { it[WELCOME_SEEN] = true }
    }

    private companion object {
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
    }
}
