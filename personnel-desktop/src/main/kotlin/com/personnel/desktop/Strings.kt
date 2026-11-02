package com.personnel.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** A UI text in French and Arabic (same texts as the Android app's strings.xml). */
class Str(val fr: String, val ar: String)

/** Current UI language. Compose state, so switching it redraws the window. */
object Lang {
    var arabic by mutableStateOf(Locale.getDefault().language == "ar")
}

fun text(s: Str, vararg args: Any): String = String.format(Locale.ROOT, if (Lang.arabic) s.ar else s.fr, *args)

/** Same call shape as Android's stringResource so the screens read the same on both. */
@Composable
fun stringResource(s: Str, vararg args: Any): String = text(s, *args)

@Suppress("ClassName")
object R {
    object string {
        val app_name = Str("Gestion du personnel", "تسيير العمال")
        val workers = Str("Travailleurs", "العمال")
        val workers_count = Str("%1\$d travailleurs", "%1\$d عامل")
        val register_counts = Str("%1\$d lignes · %2\$d ajoutées", "%1\$d سطر · %2\$d مضاف")
        val register_empty_short = Str("Vide – importez le fichier Excel", "فارغ – استورد ملف Excel")
        val register_empty_title = Str("Registre vide", "السجل فارغ")
        val register_empty_body = Str("Importez le fichier Excel déjà rempli : ses colonnes et ses lignes seront reprises, puis vous pourrez ajouter de nouvelles lignes.", "استورد ملف Excel المعبأ سابقا: ستؤخذ أعمدته وأسطره، ثم يمكنك إضافة أسطر جديدة بعده.")
        val columns_now = Str("Colonnes actuelles : %1\$s", "الأعمدة الحالية: %1\$s")
        val import_file = Str("Importer un fichier Excel", "استيراد ملف Excel")
        val import_workbook = Str("Importer un classeur (plusieurs feuilles)", "استيراد مصنف (عدة أوراق)")
        val export_file = Str("Exporter en Excel", "تصدير إلى Excel")
        val export_all = Str("Exporter tous les registres", "تصدير كل السجلات")
        val export_all_file = Str("Registres du personnel", "Registres du personnel")
        val edit_columns = Str("Colonnes", "الأعمدة")
        val add_column = Str("Ajouter une colonne", "إضافة عمود")
        val columns_warning = Str("Les valeurs restent avec leur colonne. Supprimer une colonne supprime ses valeurs.", "تبقى القيم مع عمودها. حذف عمود يحذف قيمه.")
        val add_row = Str("Ajouter", "إضافة")
        val new_row = Str("Nouvelle ligne", "سطر جديد")
        val edit_row = Str("Modifier la ligne", "تعديل السطر")
        val new_badge = Str("NOUVEAU", "جديد")
        val lines_shown = Str("%1\$d sur %2\$d lignes (les plus récentes en premier)", "%1\$d من %2\$d سطر (الأحدث أولا)")
        val search_hint = Str("Matricule, nom, fonction…", "الرقم، الاسم، الوظيفة…")
        val clear = Str("Effacer", "مسح")
        val back = Str("Retour", "رجوع")
        val save = Str("Enregistrer", "حفظ")
        val cancel = Str("Annuler", "إلغاء")
        val ok = Str("OK", "موافق")
        val delete = Str("Supprimer", "حذف")
        val pick_date = Str("Choisir la date", "اختيار التاريخ")
        val known_worker = Str("%1\$s · %2\$d dossier(s)", "%1\$s · %2\$d ملف")
        val row_empty = Str("Remplissez au moins un champ.", "املأ خانة واحدة على الأقل.")
        val delete_row_title = Str("Supprimer cette ligne ?", "حذف هذا السطر؟")
        val delete_row_body = Str("Cette action est définitive.", "لا يمكن التراجع عن الحذف.")
        val discard_title = Str("Abandonner les modifications ?", "تجاهل التعديلات؟")
        val discard = Str("Abandonner", "تجاهل")
        val keep_editing = Str("Continuer", "متابعة")
        val import_title = Str("Importer", "استيراد")
        val sheet_summary = Str("Feuille « %1\$s » : %2\$d lignes", "ورقة «%1\$s»: %2\$d سطر")
        val import_into = Str("Importer dans", "استيراد إلى")
        val skip_sheet = Str("— Ne pas importer —", "— لا تستورد —")
        val import_replaces = Str("Le contenu actuel de ces registres sera remplacé :\n%1\$s", "سيُستبدل المحتوى الحالي لهذه السجلات:\n%1\$s")
        val import_confirm = Str("Importer", "استيراد")
        val imported = Str("%1\$d registre(s) importé(s), %2\$d lignes", "تم استيراد %1\$d سجل، %2\$d سطر")
        val import_failed = Str("Échec de l'import : %1\$s", "فشل الاستيراد: %1\$s")
        val cannot_open = Str("Impossible d'ouvrir le fichier", "تعذر فتح الملف")
        val no_data = Str("Aucune donnée trouvée dans le fichier", "لا توجد بيانات في الملف")
        val exported = Str("Fichier Excel enregistré", "تم حفظ ملف Excel")
        val export_failed = Str("Échec de l'export : %1\$s", "فشل التصدير: %1\$s")
        val add_for_worker = Str("Nouveau document", "وثيقة جديدة")
        val choose_register = Str("Ajouter dans quel registre ?", "الإضافة في أي سجل؟")
        val worker_gone = Str("Ce travailleur n'apparaît plus dans les registres.", "هذا العامل لم يعد موجودا في السجلات.")
        val matricule_value = Str("Matricule : %1\$s", "الرقم: %1\$s")
        val function_value = Str("Fonction : %1\$s", "الوظيفة: %1\$s")
        val records_count = Str("%1\$d ligne(s) dans les registres", "%1\$d سطر في السجلات")
        val language_switch = Str("العربية", "Français")
        val data_folder = Str("Données : %1\$s", "البيانات: %1\$s")
    }
}
