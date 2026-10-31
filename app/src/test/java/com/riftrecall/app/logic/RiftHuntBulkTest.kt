package com.riftrecall.app.logic

import com.riftrecall.app.data.RiftHunt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the real shape of https://api.rifthunt.com/bulk/cards (checked 2026-10-31). */
class RiftHuntBulkTest {
    private val body = """
        {"fetched":"2026-10-29T19:06:16.956Z","source":"riotgames.com",
         "sets":[{"id":"RAD","name":"Radiance","count":250,"released":"2026-10-23"}],
         "cards":[
          {"id":"ogn-001-298","riftboundId":"ogn-001-298","name":"Blazing Scorcher","cleanName":"Blazing Scorcher",
           "num":1,"energy":5,"might":5,"power":null,"type":"Unit","supertype":null,"rarity":"Common",
           "domains":["Fury"],"text":"[Accelerate] (You may pay :rb_energy_1::rb_rune_fury: as an additional cost to have me enter ready.)",
           "rich":"<p>x</p>","flavour":null,"set":"OGN","setName":"Origins","tags":["Dragon","Noxus"],"artist":"Envar Studio",
           "tcgId":"652771","imgUrl":"https://cmsassets.rgpub.io/a.png","img":"cards/ogn-001-298.png",
           "orientation":"portrait","alt":false,"sig":false,"over":false,"imgHash":"8de"},
          {"id":"rad-010-250a","name":"Some Radiant Card","num":10,"energy":3,"might":2,"type":"Unit","rarity":"Rare",
           "domains":["Calm","Body"],"text":"Hello.","set":"RAD","setName":"Radiance","tags":[],
           "imgUrl":"https://cmsassets.rgpub.io/b.png","orientation":"portrait","alt":true,"sig":false,"over":false},
          {"id":"rad-bf-1","name":"Some Battlefield","num":3,"type":"Battlefield","rarity":"Rare","domains":[],
           "text":"","set":"RAD","imgUrl":"https://cmsassets.rgpub.io/c.png","orientation":"landscape"},
          {"id":"broken","name":"No image","set":"RAD","num":9}
         ]}
    """.trimIndent()

    @Test fun parsesBulkWrapperAndKeepsRadiance() {
        val cards = RiftHunt.parse(body)
        assertEquals(3, cards.size) // the card without an image is skipped
        val scorcher = cards.first { it.name == "Blazing Scorcher" }
        assertEquals("ogn-001-298", scorcher.id)
        assertEquals(5, scorcher.energy)
        assertNull(scorcher.power)
        assertEquals(listOf("Fury"), scorcher.domains)
        assertEquals("https://cmsassets.rgpub.io/a.png", scorcher.imageUrl)
        assertTrue(scorcher.text.contains(":rb_energy_1:"))
        assertFalse(scorcher.special)

        val rad = cards.first { it.name == "Some Radiant Card" }
        assertEquals("RAD", rad.setId)
        assertTrue(rad.special) // alt-art printing
        assertEquals(listOf("Calm", "Body"), rad.domains)

        assertTrue(cards.first { it.type == "Battlefield" }.landscape)
    }
}

class ReprintTest {
    private fun c(id: String, set: String, name: String = "Banner of Conquest", type: String = "Gear") =
        com.riftrecall.app.data.Card(id = id, name = name, setId = set, type = type, imageUrl = "https://x/$id.png")

    @Test fun cleaningKeepsReprintsPerSetAndPoolDedupesAcrossSets() {
        val all = listOf(c("sfd-1", "SFD"), c("rad-1", "RAD"), c("opp-1", "OPP"), c("rad-2", "RAD", "Other", "Unit"))
        val cleaned = com.riftrecall.app.data.CardCleaner.clean(all)
        assertEquals(4, cleaned.size) // one per set, nothing dropped

        // Everything selected: the reprint is quizzed once, as the earliest main-set printing.
        val everything = com.riftrecall.app.data.Pool.filter(cleaned, com.riftrecall.app.data.Settings())
        assertEquals(setOf("sfd-1", "rad-2"), everything.map { it.id }.toSet())

        // Only Radiance selected: its reprint is still there.
        val radOnly = com.riftrecall.app.data.Pool.filter(cleaned, com.riftrecall.app.data.Settings(sets = setOf("RAD")))
        assertEquals(setOf("rad-1", "rad-2"), radOnly.map { it.id }.toSet())
    }
}
