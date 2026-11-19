package com.example.financetracker.ai.parse

import com.example.financetracker.domain.model.DefaultCategories

/**
 * keyword (lowercase, may contain spaces for phrases like "ice cream") -> DefaultCategories id.
 * Matching tolerates simple plurals ("coffees", "bills", "groceries"). Pure Kotlin.
 */
class CategoryKeywords(entries: Map<String, String>) {

    private val single: Map<String, String>
    private val phrases: List<Pair<List<String>, String>>

    init {
        val s = HashMap<String, String>()
        val p = mutableListOf<Pair<List<String>, String>>()
        for ((k, v) in entries) {
            val words = k.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            when {
                words.isEmpty() -> Unit
                words.size == 1 -> s[words[0]] = v
                else -> p += words to v
            }
        }
        single = s
        phrases = p.sortedByDescending { it.first.size }
    }

    /** Number of words of a multi-word keyword that starts at [index] in [words] (lowercase), or 0. */
    fun phraseLengthAt(words: List<String>, index: Int): Int = phraseAt(words, index)?.first?.size ?: 0

    /** Category of the first keyword found in [words] (lowercase tokens), or null. */
    fun categoryFor(words: List<String>): String? {
        for (i in words.indices) {
            phraseAt(words, i)?.let { return it.second }
            for (f in forms(words[i])) single[f]?.let { return it }
        }
        return null
    }

    private fun phraseAt(words: List<String>, index: Int): Pair<List<String>, String>? =
        phrases.firstOrNull { (pw, _) ->
            index + pw.size <= words.size && pw.indices.all { i -> pw[i] in forms(words[index + i]) }
        }

    companion object {
        /** The word itself plus simple singular stems ("coffees" -> "coffee", "groceries" -> "grocery"). */
        fun forms(word: String): List<String> {
            val w = word.lowercase()
            val out = LinkedHashSet<String>()
            out.add(w)
            if (w.length > 3 && w.endsWith("ies")) out.add(w.dropLast(3) + "y")
            if (w.length > 2 && w.endsWith("es")) out.add(w.dropLast(2))
            if (w.length > 1 && w.endsWith("s")) out.add(w.dropLast(1))
            return out.toList()
        }

        private fun map(vararg groups: Pair<String, String>): Map<String, String> {
            val m = HashMap<String, String>()
            for ((id, words) in groups) {
                for (w in words.split(',')) {
                    val k = w.trim()
                    if (k.isNotEmpty()) m[k] = id
                }
            }
            return m
        }

        val Default: CategoryKeywords = CategoryKeywords(
            map(
                DefaultCategories.FOOD to "tea,chai,coffee,lunch,dinner,breakfast,brunch,snack,snacks,swiggy,zomato," +
                    "pizza,burger,biryani,juice,food,meal,restaurant,dosa,sandwich,ice cream,icecream,cake,dessert,samosa,maggi",
                DefaultCategories.TRANSPORT to "uber,ola,rapido,auto,auto rickshaw,rickshaw,metro,petrol,diesel,fuel,bus,train," +
                    "cab,taxi,flight,toll,parking,fastag",
                DefaultCategories.ENTERTAINMENT to "movie,film,cinema,netflix,concert,game,gaming,spotify,hotstar,prime video,party,pub",
                DefaultCategories.SHOPPING to "amazon,flipkart,myntra,clothes,cloth,shirt,tshirt,jeans,shoes,shopping,dress",
                DefaultCategories.BILLS to "rent,electricity,wifi,internet,recharge,bill,water,insurance,emi,broadband,gas cylinder,postpaid",
                DefaultCategories.HEALTH to "medicine,doctor,pharmacy,hospital,medical,dentist,clinic,gym,checkup",
                DefaultCategories.GROCERIES to "milk,vegetable,vegetables,veggies,grocery,groceries,blinkit,zepto,bigbasket,instamart,fruit,fruits,eggs,egg,bread",
                DefaultCategories.SALARY to "salary,salaries,paycheck,payroll,wages,stipend",
            ),
        )
    }
}
