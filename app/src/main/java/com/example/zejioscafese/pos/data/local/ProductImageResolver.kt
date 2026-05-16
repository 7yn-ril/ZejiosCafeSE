package com.example.zejioscafese.pos.data.local

object ProductImageResolver {

    private const val ASSET_PREFIX = "asset:///product_images/"

    private val assetFilesByProductKey = mapOf(
        "alohaburger" to "AlohaBurger.PNG",
        "alohasquared" to "AlohaSquared.JPG",
        "americano" to "Americano.png",
        "bananachoco" to "BananaChoco.png",
        "bananaclassic" to "BananaClassic.png",
        "bananapeanut" to "BananaPeanut.png",
        "beefmushpepper" to "BeefMushPepper.PNG",
        "beefquesadillas" to "BeefQuesadillas.JPG",
        "beefynachos" to "BeefyNachosOverload.PNG",
        "beefynachossolo" to "BeefyNachosSolo.PNG",
        "berrymatchalatte" to "BerryMatchaLatte.png",
        "biscoffeefrappuccino" to "BiscoffeeFrappuccino.png",
        "biscoffeelatte" to "BiscoffeeLatte.png",
        "blackforestcheesecake" to "BlackForestCheesecake.png",
        "blueberryfruittea" to "BlueberryFruitTea.png",
        "blueberrypureefrappe" to "BlueberryPureeFrappe.png",
        "bottledwater" to "BottledWater.png",
        "buffalowings" to "BuffaloWings.PNG",
        "burgersteak" to "BurgerSteak.PNG",
        "caramelmaccfrappuccino" to "CaramelMaccFrappuccino.png",
        "caramelmacchiato" to "CaramelMacchiato.png",
        "caramelmacchiatofrappuccino" to "CaramelMaccFrappuccino.png",
        "caramelsugarmilktea" to "CaramelSugarMilktea.png",
        "cheesyhungarian" to "CheeseHungarian.JPG",
        "chickenburger" to "ChickenBurger.PNG",
        "chickenfingers" to "ChickenFingers.PNG",
        "chickenpoppers" to "ChickenPoppers.JPG",
        "chocolatechipsmilktea" to "ChocolateChipsMilktea.png",
        "chocomadnessfrappe" to "ChocoMadnessFrappe.png",
        "chocomilktea" to "ChocoMilktea.png",
        "darkmocha" to "DarkMocha.png",
        "dolcemudfrappuccino" to "DolceMudFrappuccino.png",
        "espressokelapa" to "EspressoKelapa.png",
        "espressoshot" to "EspressoShot.png",
        "fettuccinecarbonara" to "FettuccineCarbonara.JPG",
        "fishfillet" to "FishFillet.PNG",
        "fishfilletwithrice" to "FishFillet.PNG",
        "greenapplefruittea" to "GreenAppleFruitTea.png",
        "greenislandlemonade" to "GreenIslandLemonade.png",
        "homemadefries" to "HomemadeFries.PNG",
        "homemadeporksiomai" to "HomemadePorkSiomai.PNG",
        "homemadepotatomojos" to "HomemadePotatoMojos.PNG",
        "honeygarlicwings" to "HoneyGarlicWings.PNG",
        "latte" to "Latte.jpg",
        "longsilog" to "Longsilog.JPG",
        "lycheefruittea" to "LycheeFruitTea.png",
        "lycheelemonade" to "LycheeLemonade.png",
        "matchaespresso" to "MatchaEspresso.png",
        "matchafrappe" to "MatchaFrappe.png",
        "matchakremapuff" to "MatchaKremaPuff.png",
        "matchalatte" to "MatchaLatte.png",
        "matchaoatlatte" to "MatchaOatLatte.png",
        "milk" to "Milk.png",
        "oatmilk" to "Oatmilk.png",
        "okinawamilktea" to "OkinawaMilktea.png",
        "oreocheesecakemilktea" to "OreoCheesecakeMilktea.png",
        "peppermintmocha" to "PeppermintMocha.png",
        "porksisig" to "PorkSisig.PNG",
        "porksisigwithrice" to "PorkSisig.PNG",
        "raspberrybushfrappe" to "RaspberryBushFrappe.png",
        "roastedalmondlatte" to "RoastedAlmondLatte.png",
        "saltedcaramel" to "SaltedCaramel.png",
        "seasaltlatte" to "SeaSaltLatte.png",
        "spanishlatte" to "SpanishLatte.png",
        "spanishlattebreve" to "SpanishLatteBreve.png",
        "specialburger" to "SpecialBurger.JPG",
        "specialtwins" to "SpecialTwins.PNG",
        "strawberryfruittea" to "StrawberryFruitTea.png",
        "strawberrylemonade" to "StrawberryLemonade.png",
        "strawberrypureefrappe" to "StrawberryPureeFrappe.png",
        "taromilktea" to "TaroMilktea.png",
        "taroviola" to "TaroViola.png",
        "teriyakiwings" to "TeriyakiWings.JPG",
        "vietnameseblack" to "VietnameseBlack.png",
        "vietnamesecoffee" to "VietnameseCoffee.png",
        "whippedcream" to "WhippedCream.png",
        "whitemocha" to "WhiteMocha.jpeg",
        "whitemochafrappuccino" to "WhiteMochaFrappucinno.png",
        "wintermelonmilktea" to "WintermelonMilktea.png",
        "yakult" to "Yakult.png",
    )

    fun resolve(productName: String?): String? {
        val normalizedKey = normalize(productName)
        val assetFileName = assetFilesByProductKey[normalizedKey] ?: return null
        return ASSET_PREFIX + assetFileName
    }

    private fun normalize(value: String?): String {
        return value
            .orEmpty()
            .lowercase(java.util.Locale.US)
            .replace(Regex("[^a-z0-9]"), "")
    }
}
