package com.expenso.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object IconMapper {
    fun getIcon(name: String): ImageVector {
        return when (name.lowercase()) {
            "account_balance_wallet" -> Icons.Rounded.AccountBalanceWallet
            "account_box" -> Icons.Rounded.AccountBox
            "account_circle" -> Icons.Rounded.AccountCircle
            "assured_workload" -> Icons.Rounded.AssuredWorkload
            "business_center" -> Icons.Rounded.BusinessCenter
            "card_membership" -> Icons.Rounded.CardMembership
            "store" -> Icons.Rounded.Store
            "price_check" -> Icons.Rounded.PriceCheck
            "request_quote" -> Icons.Rounded.RequestQuote
            "calculate" -> Icons.Rounded.Calculate
            "pie_chart" -> Icons.Rounded.PieChart
            "analytics" -> Icons.Rounded.Analytics
            "point_of_sale" -> Icons.Rounded.PointOfSale
            "wallet" -> Icons.Rounded.Wallet
            "account_balance" -> Icons.Rounded.AccountBalance
            "credit_card" -> Icons.Rounded.CreditCard
            "payments" -> Icons.Rounded.Payments
            "storefront" -> Icons.Rounded.Storefront
            "trending_up" -> Icons.AutoMirrored.Rounded.TrendingUp
            "card_giftcard" -> Icons.Rounded.CardGiftcard
            "restaurant" -> Icons.Rounded.Restaurant
            "shopping_bag" -> Icons.Rounded.ShoppingBag
            "directions_car" -> Icons.Rounded.DirectionsCar
            "receipt_long" -> Icons.AutoMirrored.Rounded.ReceiptLong
            "medical_services" -> Icons.Rounded.MedicalServices
            "sports_esports" -> Icons.Rounded.SportsEsports
            "person" -> Icons.Rounded.Person
            "star" -> Icons.Rounded.Star
            "savings" -> Icons.Rounded.Savings
            "schedule" -> Icons.Rounded.Schedule
            "settings" -> Icons.Rounded.Settings
            "computer", "laptop" -> Icons.Rounded.Laptop
            "home" -> Icons.Rounded.Home
            "shopping_cart" -> Icons.Rounded.ShoppingCart
            "school" -> Icons.Rounded.School
            "shield" -> Icons.Rounded.Shield
            "devices" -> Icons.Rounded.Devices
            "more_horiz" -> Icons.Rounded.MoreHoriz
            "electric_bolt" -> Icons.Rounded.ElectricBolt
            "water_drop" -> Icons.Rounded.WaterDrop
            "train" -> Icons.Rounded.Train
            "flight" -> Icons.Rounded.Flight
            "fastfood" -> Icons.Rounded.Fastfood
            "local_dining" -> Icons.Rounded.LocalDining
            "fitness_center" -> Icons.Rounded.FitnessCenter
            "movie" -> Icons.Rounded.Movie
            "wifi" -> Icons.Rounded.Wifi
            "smartphone" -> Icons.Rounded.Smartphone
            "apartment" -> Icons.Rounded.Apartment
            
            // Food group
            "table_bar" -> Icons.Rounded.TableBar
            "bakery_dining" -> Icons.Rounded.BakeryDining
            "spa" -> Icons.Rounded.Spa
            "shopping_basket" -> Icons.Rounded.ShoppingBasket
            "local_grocery_store" -> Icons.Rounded.LocalGroceryStore
            "local_drink" -> Icons.Rounded.LocalDrink
            "egg" -> Icons.Rounded.Egg
            "lunch_dining" -> Icons.Rounded.LunchDining
            "local_cafe" -> Icons.Rounded.LocalCafe
            "cake" -> Icons.Rounded.Cake
            "free_breakfast" -> Icons.Rounded.FreeBreakfast
            "local_bar" -> Icons.Rounded.LocalBar
            
            // Travel group
            "map" -> Icons.Rounded.Map
            "local_gas_station" -> Icons.Rounded.LocalGasStation
            "local_parking" -> Icons.Rounded.LocalParking
            "directions_motorcycle" -> Icons.Rounded.TwoWheeler
            "two_wheeler" -> Icons.Rounded.TwoWheeler
            "directions_bus" -> Icons.Rounded.DirectionsBus
            "directions_railway" -> Icons.Rounded.DirectionsRailway
            "hotel" -> Icons.Rounded.Hotel
            
            // Shopping group
            "local_offer" -> Icons.Rounded.LocalOffer
            "monitor" -> Icons.Rounded.Monitor
            "checkroom" -> Icons.Rounded.Checkroom
            "chair" -> Icons.Rounded.Chair
            "kitchen" -> Icons.Rounded.Kitchen
            
            // Home & Family group
            "groups" -> Icons.Rounded.Groups
            "pets" -> Icons.Rounded.Pets
            "child_care" -> Icons.Rounded.ChildCare
            
            // Entertainment group
            "play_circle" -> Icons.Rounded.PlayCircle
            "headphones" -> Icons.Rounded.Headphones
            
            // Business group
            "local_shipping" -> Icons.Rounded.LocalShipping
            "business" -> Icons.Rounded.Business
            "local_post_office" -> Icons.Rounded.LocalPostOffice
            "campaign" -> Icons.Rounded.Campaign
            
            // Finance group
            "bar_chart" -> Icons.Rounded.BarChart
            "volunteer_activism" -> Icons.Rounded.VolunteerActivism
            "monetization_on" -> Icons.Rounded.MonetizationOn
            "currency_exchange" -> Icons.Rounded.CurrencyExchange
            "percent" -> Icons.Rounded.Percent
            "upi" -> Icons.Rounded.QrCodeScanner
            "online_payment" -> Icons.Rounded.Contactless
            "rupee" -> Icons.Rounded.CurrencyRupee
            
            // Medical group
            "favorite" -> Icons.Rounded.Favorite
            "local_pharmacy" -> Icons.Rounded.LocalPharmacy
            "healing" -> Icons.Rounded.Healing
            
            // Utilities group
            "receipt" -> Icons.Rounded.Receipt
            "build" -> Icons.Rounded.Build
            "fireplace" -> Icons.Rounded.Fireplace
            "phone_android" -> Icons.Rounded.PhoneAndroid
            "cleaning_services" -> Icons.Rounded.CleaningServices
            "local_laundry_service" -> Icons.Rounded.LocalLaundryService
            "water" -> Icons.Rounded.Water
            
            // Miscellaneous group
            "grass" -> Icons.Rounded.Grass
            "sync" -> Icons.Rounded.Sync
            "smoking_rooms" -> Icons.Rounded.SmokingRooms
            "work" -> Icons.Rounded.Work
            "bathtub" -> Icons.Rounded.Bathtub
            "luggage" -> Icons.Rounded.Luggage
            "emoji_events" -> Icons.Rounded.EmojiEvents
            "casino" -> Icons.Rounded.Casino
            
            // Lifestyle & Hobbies group
            "brush" -> Icons.Rounded.Brush
            "palette" -> Icons.Rounded.Palette
            "piano" -> Icons.Rounded.Piano
            "directions_bike" -> Icons.Rounded.DirectionsBike
            "local_florist" -> Icons.Rounded.LocalFlorist
            "self_improvement" -> Icons.Rounded.SelfImprovement
            "icecream" -> Icons.Rounded.Icecream
            "smart_toy" -> Icons.Rounded.SmartToy
            "tv" -> Icons.Rounded.Tv
            "vpn_key" -> Icons.Rounded.VpnKey
            "content_cut" -> Icons.Rounded.ContentCut
            "celebration" -> Icons.Rounded.Celebration
            
            // Education group
            "book" -> Icons.Rounded.Book
            "edit" -> Icons.Rounded.Edit
            "science" -> Icons.Rounded.Science
            "calculate" -> Icons.Rounded.Calculate
            "menu_book" -> Icons.Rounded.MenuBook
            
            else -> Icons.AutoMirrored.Rounded.Help
        }
    }

    fun getIconForCategory(categoryName: String, fallbackIcon: String): ImageVector {
        return getIcon(fallbackIcon)
    }

    val availableIcons = listOf(
        "account_balance_wallet",
        "account_box",
        "account_circle",
        "assured_workload",
        "business_center",
        "card_membership",
        "store",
        "price_check",
        "request_quote",
        "calculate",
        "pie_chart",
        "analytics",
        "point_of_sale",
        "wallet",
        "account_balance",
        "credit_card",
        "payments",
        "storefront",
        "trending_up",
        "emoji_events",
        "casino",
        "card_giftcard",
        "restaurant",
        "shopping_bag",
        "directions_car",
        "receipt_long",
        "medical_services",
        "sports_esports",
        "person",
        "star",
        "savings",
        "schedule",
        "settings",
        "computer",
        "home",
        "shopping_cart",
        "school",
        "shield",
        "devices",
        "more_horiz",
        "electric_bolt",
        "water_drop",
        "train",
        "flight",
        "fastfood",
        "local_dining",
        "fitness_center",
        "movie",
        "wifi",
        "brush",
        "palette",
        "piano",
        "directions_bike",
        "local_florist",
        "self_improvement",
        "icecream",
        "smart_toy",
        "tv",
        "vpn_key",
        "content_cut",
        "celebration",
        "book",
        "edit",
        "science",
        "calculate",
        "menu_book",
        "upi",
        "online_payment",
        "rupee"
    )

    val groupedIcons = mapOf(
        "Food" to listOf("restaurant", "table_bar", "bakery_dining", "local_drink", "egg", "lunch_dining", "local_cafe", "cake", "local_bar", "fastfood", "local_dining"),
        "Travel" to listOf("map", "local_gas_station", "local_parking", "directions_car", "two_wheeler", "directions_bus", "train", "flight", "hotel", "directions_railway", "luggage"),
        "Shopping" to listOf("shopping_cart", "local_offer", "monitor", "checkroom", "shopping_bag", "chair", "kitchen", "computer", "card_giftcard", "shopping_basket"),
        "Home & Family" to listOf("groups", "pets", "home", "child_care", "person"),
        "Education" to listOf("school", "book", "edit", "science", "calculate", "menu_book"),
        "Entertainment" to listOf("sports_esports", "movie", "play_circle", "headphones", "casino", "emoji_events"),
        "Lifestyle & Hobbies" to listOf("brush", "palette", "piano", "directions_bike", "local_florist", "self_improvement", "icecream", "smart_toy", "tv", "vpn_key", "content_cut", "celebration"),
        "Business" to listOf("business", "local_shipping", "campaign", "storefront", "local_post_office", "work"),
        "Finance" to listOf("trending_up", "bar_chart", "shield", "volunteer_activism", "payments", "savings", "currency_exchange", "wallet", "credit_card", "receipt_long", "percent", "account_balance", "upi", "online_payment", "rupee", "price_check", "request_quote", "pie_chart", "analytics", "point_of_sale"),
        "Medical" to listOf("favorite", "medical_services", "local_pharmacy", "healing", "fitness_center", "spa"),
        "Utilities" to listOf("receipt", "build", "electric_bolt", "fireplace", "phone_android", "cleaning_services", "local_laundry_service", "water", "water_drop", "wifi", "settings", "schedule", "devices"),
        "Miscellaneous" to listOf("grass", "sync", "smoking_rooms", "bathtub", "more_horiz", "star")
    )

    // 45 distinct dynamic colors
    val availableColors = listOf(
        // Reds/Oranges
        "#FFABAB", "#FFADAD", "#F08080", "#FFAAA5", "#FFC3A0", "#FFD3B6", "#FFDAB9", "#FFDFBA", "#FFD6A5",
        // Yellows
        "#FFEFD5", "#FFE4B5", "#FFFACD", "#FCF6BD", "#EEE8AA", "#FAFAD2", "#FFFFBA", "#FFFFFC", "#FDFFB6",
        // Greens
        "#DCEDC1", "#C5E1A5", "#E2F0D9", "#CAFFBF", "#BFFCC6", "#BAFFC9", "#D0F4DE", "#B3FFD4", "#A8E6CF",
        // Blues/Purples
        "#9BF6FF", "#A9DEF9", "#BAE1FF", "#B3D4FF", "#A0C4FF", "#BDB2FF", "#D4B3FF", "#D5AAFF", "#E4C1F9",
        // Pinks
        "#FFC6FF", "#FFB3F7", "#FFB5E8", "#FFC9DE", "#FF9EBB", "#FFB8C1", "#FCE1E4", "#FFB3BA", "#FFE4E1"
    )

    private val allStandardIcons: Set<String> by lazy {
        groupedIcons.values.flatten().toSet() + availableIcons.toSet()
    }

    @Composable
    fun CategoryIcon(
        icon: String,
        categoryName: String,
        tint: Color,
        modifier: Modifier = Modifier
    ) {
        val isCustom = icon.isNotEmpty() && !allStandardIcons.contains(icon.lowercase()) && icon.lowercase() != "wallet" && icon.lowercase() != "trending_up" && icon.lowercase() != "trending_down"
        if (isCustom) {
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                modifier = modifier
            )
        } else {
            Icon(
                imageVector = getIconForCategory(categoryName, icon),
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )
        }
    }
}
