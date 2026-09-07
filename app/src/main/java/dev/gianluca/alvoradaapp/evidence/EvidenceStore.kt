package dev.gianluca.alvoradaapp.evidence

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * Onde as fotos de evidência moram.
 *
 * `filesDir` — storage privado do app — e não a galeria do sistema. Três razões:
 * dispensa permissão de armazenamento, não polui o rolo de câmera com dezenas de fotos
 * de caixa de remédio, e mantém o material fora de backups automáticos. O preço é que
 * desinstalar apaga tudo, o que torna o export da Fase 5 obrigatório, não opcional.
 *
 * A pasta por dia (`evidence/2026-08-02/`) é o que faz a galeria da Fase 3 conseguir
 * agrupar por data sem consultar o banco.
 */
class EvidenceStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "evidence").apply { mkdirs() }

    fun dayDir(date: String): File = File(root, date).apply { mkdirs() }

    /** Nome estável e único: instância + carimbo, para duas fotos nunca colidirem. */
    fun newPhotoFile(date: String, missionInstanceId: Long): File =
        File(dayDir(date), "${missionInstanceId}_${System.currentTimeMillis()}.jpg")

    fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /** Datas com pelo menos uma foto, mais recente primeiro. Base da galeria. */
    fun daysWithPhotos(): List<String> =
        root.listFiles { f -> f.isDirectory && f.list()?.isNotEmpty() == true }
            ?.map { it.name }
            ?.sortedDescending()
            .orEmpty()

    companion object {
        fun today(): String = LocalDate.now().toString()
    }
}
