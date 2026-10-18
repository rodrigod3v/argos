package com.argos.core.text

/**
 * Grafias alternativas de um nome estrangeiro na ortografia portuguesa.
 *
 * O modelo Vosk pt-BR nao conhece "chrome" e produz fonemas que nao casam com
 * a fala real — mesmo dentro de uma gramatica. Reescrever na grafia que o
 * brasileiro usa resolve: "crome", "cromi" o modelo le como "chrome".
 *
 * Puro: sem Android, testavel em JVM.
 */
object PronunciationVariants {

    /** quantas grafias no maximo por nome (controla o tamanho da gramatica) */
    const val DEFAULT_LIMIT = 3

    fun of(name: String, limit: Int = DEFAULT_LIMIT): List<String> {
        val base = name.norm()
        if (base.isEmpty()) return emptyList()

        val variants = LinkedHashSet<String>()
        variants += base

        val substituted = base
            .replace("ch", "c")
            .replace("sh", "ch")
            .replace("ph", "f")
            .replace("th", "t")
            .replace("oo", "u")
            .replace("ee", "i")
            .replace("y", "i")
            .replace("w", "u")
        if (substituted != base) variants += substituted

        // 'e' final em ingles soa "i" em portugues: chrome -> cromi
        if (base.endsWith("e") && base.length > 3) {
            variants += base.dropLast(1)
            variants += substituted.dropLast(1)
            variants += substituted.dropLast(1) + "i"
        }

        return variants.filter { it.isNotBlank() }.take(limit)
    }
}
