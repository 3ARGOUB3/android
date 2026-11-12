package com.nephrocare.core

/** A city for prayer times and the centers directory. [latin] is used in French, English and Spanish. */
data class City(val id: String, val arabic: String, val latin: String, val latitude: Double, val longitude: Double) {
    fun name(language: String) = if (language == "ar") arabic else latin
}

object Cities {
    /** The wilayas (provincial capitals) of Algeria, then a few cities abroad. */
    val all: List<City> = listOf(
        City("algiers", "الجزائر العاصمة", "Alger", 36.7538, 3.0588),
        City("oran", "وهران", "Oran", 35.6971, -0.6308),
        City("constantine", "قسنطينة", "Constantine", 36.3650, 6.6147),
        City("annaba", "عنابة", "Annaba", 36.9000, 7.7667),
        City("blida", "البليدة", "Blida", 36.4700, 2.8277),
        City("batna", "باتنة", "Batna", 35.5550, 6.1741),
        City("setif", "سطيف", "Sétif", 36.1911, 5.4137),
        City("djelfa", "الجلفة", "Djelfa", 34.6704, 3.2504),
        City("biskra", "بسكرة", "Biskra", 34.8504, 5.7280),
        City("tlemcen", "تلمسان", "Tlemcen", 34.8783, -1.3150),
        City("bejaia", "بجاية", "Béjaïa", 36.7509, 5.0567),
        City("tizi_ouzou", "تيزي وزو", "Tizi Ouzou", 36.7169, 4.0497),
        City("chlef", "الشلف", "Chlef", 36.1650, 1.3345),
        City("sidi_bel_abbes", "سيدي بلعباس", "Sidi Bel Abbès", 35.1899, -0.6309),
        City("skikda", "سكيكدة", "Skikda", 36.8762, 6.9092),
        City("mostaganem", "مستغانم", "Mostaganem", 35.9310, 0.0890),
        City("medea", "المدية", "Médéa", 36.2642, 2.7539),
        City("mascara", "معسكر", "Mascara", 35.3980, 0.1403),
        City("ouargla", "ورقلة", "Ouargla", 31.9493, 5.3250),
        City("ghardaia", "غرداية", "Ghardaïa", 32.4909, 3.6735),
        City("bechar", "بشار", "Béchar", 31.6167, -2.2167),
        City("tamanrasset", "تمنراست", "Tamanrasset", 22.7850, 5.5228),
        City("adrar", "أدرار", "Adrar", 27.8743, -0.2939),
        City("el_oued", "الوادي", "El Oued", 33.3683, 6.8674),
        City("laghouat", "الأغواط", "Laghouat", 33.8000, 2.8650),
        City("tiaret", "تيارت", "Tiaret", 35.3711, 1.3170),
        City("jijel", "جيجل", "Jijel", 36.8206, 5.7667),
        City("guelma", "قالمة", "Guelma", 36.4621, 7.4261),
        City("souk_ahras", "سوق أهراس", "Souk Ahras", 36.2864, 7.9511),
        City("tebessa", "تبسة", "Tébessa", 35.4042, 8.1242),
        City("msila", "المسيلة", "M'Sila", 35.7058, 4.5419),
        City("bouira", "البويرة", "Bouira", 36.3800, 3.9000),
        City("boumerdes", "بومرداس", "Boumerdès", 36.7664, 3.4772),
        City("tipaza", "تيبازة", "Tipaza", 36.5897, 2.4475),
        City("relizane", "غليزان", "Relizane", 35.7373, 0.5559),
        City("khenchela", "خنشلة", "Khenchela", 35.4358, 7.1433),
        City("oum_el_bouaghi", "أم البواقي", "Oum El Bouaghi", 35.8775, 7.1135),
        City("mila", "ميلة", "Mila", 36.4503, 6.2644),
        City("ain_defla", "عين الدفلى", "Aïn Defla", 36.2641, 1.9679),
        City("saida", "سعيدة", "Saïda", 34.8303, 0.1517),
        City("el_bayadh", "البيض", "El Bayadh", 33.6831, 1.0193),
        City("naama", "النعامة", "Naâma", 33.2667, -0.3167),
        City("tindouf", "تندوف", "Tindouf", 27.6711, -8.1474),
        City("illizi", "إليزي", "Illizi", 26.4833, 8.4667),
        City("bordj_bou_arreridj", "برج بوعريريج", "Bordj Bou Arréridj", 36.0731, 4.7611),
        City("el_tarf", "الطارف", "El Tarf", 36.7672, 8.3137),
        City("tissemsilt", "تيسمسيلت", "Tissemsilt", 35.6072, 1.8109),
        City("ain_temouchent", "عين تموشنت", "Aïn Témouchent", 35.2975, -1.1404),
        City("paris", "باريس", "Paris", 48.8566, 2.3522),
        City("lyon", "ليون", "Lyon", 45.7640, 4.8357),
        City("marseille", "مرسيليا", "Marseille", 43.2965, 5.3698),
        City("tunis", "تونس", "Tunis", 36.8065, 10.1815),
        City("casablanca", "الدار البيضاء", "Casablanca", 33.5731, -7.5898),
        City("madrid", "مدريد", "Madrid", 40.4168, -3.7038),
        City("barcelona", "برشلونة", "Barcelona", 41.3874, 2.1686),
        City("makkah", "مكة المكرمة", "La Mecque / Mecca", 21.4225, 39.8262),
    )

    fun byId(id: String?): City? = all.firstOrNull { it.id == id }
}
