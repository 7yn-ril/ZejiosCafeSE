package com.example.zejioscafese.pos.data.local

object ProductImageResolver {

    private const val ASSET_PREFIX = "asset:///product_images/"

    private val assetFilesByProductKey = mapOf(
        "alohaburger" to "AlohaBurger.PNG",
        "alohasquared" to "AlohaSquared.JPG",
        "beefmushpepper" to "BeefMushPepper.PNG",
        "beefquesadillas" to "BeefQuesadillas.JPG",
        "beefynachos" to "BeefyNachosOverload.PNG",
        "beefynachossolo" to "BeefyNachosSolo.PNG",
        "buffalowings" to "BuffaloWings.PNG",
        "burgersteak" to "BurgerSteak.PNG",
        "cheesyhungarian" to "CheeseHungarian.JPG",
        "chickenburger" to "ChickenBurger.PNG",
        "chickenfingers" to "ChickenFingers.PNG",
        "chickenpoppers" to "ChickenPoppers.JPG",
        "fettuccinecarbonara" to "FettuccineCarbonara.JPG",
        "fishfillet" to "FishFillet.PNG",
        "fishfilletwithrice" to "FishFillet.PNG",
        "homemadefries" to "HomemadeFries.PNG",
        "homemadeporksiomai" to "HomemadePorkSiomai.PNG",
        "homemadepotatomojos" to "HomemadePotatoMojos.PNG",
        "honeygarlicwings" to "HoneyGarlicWings.PNG",
        "longsilog" to "Longsilog.JPG",
        "porksisig" to "PorkSisig.PNG",
        "porksisigwithrice" to "PorkSisig.PNG",
        "specialburger" to "SpecialBurger.JPG",
        "specialtwins" to "SpecialTwins.PNG",
        "teriyakiwings" to "TeriyakiWings.JPG"
    )

    fun resolve(productName: String?): String? {
        val normalizedKey = normalize(productName)
        val assetFileName = assetFilesByProductKey[normalizedKey] ?: return null
        return ASSET_PREFIX + assetFileName
    }

    private fun normalize(value: String?): String {
        return value
            .orEmpty()
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
