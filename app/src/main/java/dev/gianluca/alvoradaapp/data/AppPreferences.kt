package dev.gianluca.alvoradaapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /**
     * Tema escolhido. [ThemeChoice.SYSTEM] enquanto ninguém escolher — que é o
     * comportamento que a V1 tinha, e o certo para quem já configurou o aparelho.
     *
     * Leitura tolerante: um valor desconhecido cai no sistema em vez de derrubar o
     * app, na mesma linha do conversor de `RepeatKind`.
     */
    val themeChoice: Flow<ThemeChoice> = store.data.map { prefs ->
        prefs[THEME_CHOICE]
            ?.let { name -> runCatching { ThemeChoice.valueOf(name) }.getOrNull() }
            ?: ThemeChoice.SYSTEM
    }

    suspend fun setThemeChoice(choice: ThemeChoice) {
        store.edit { it[THEME_CHOICE] = choice.name }
    }

    private companion object {
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val THEME_CHOICE = stringPreferencesKey("theme_choice")
    }
}

/** As três respostas possíveis para "claro ou escuro?". */
enum class ThemeChoice(val label: String) {
    SYSTEM("Seguir o sistema"),
    LIGHT("Claro"),
    DARK("Escuro"),
}
