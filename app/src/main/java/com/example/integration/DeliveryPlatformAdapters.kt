package com.example.integration

import com.example.ai.OrderOcrParser
import com.example.model.DeliveryPlatform
import com.example.model.OrderInputSource
import com.example.model.ParsedOrderDraft
import java.util.Locale

interface DeliveryPlatformAdapter {
    val platform: DeliveryPlatform
    fun supportsPackage(packageName: String): Boolean
    fun extractDraft(
        title: String,
        body: String,
        source: OrderInputSource
    ): ParsedOrderDraft?
}

abstract class BaseDeliveryPlatformAdapter : DeliveryPlatformAdapter {
    override fun supportsPackage(packageName: String): Boolean {
        return platform.knownPackages.any { it.equals(packageName, ignoreCase = true) }
    }

    protected fun parseUnified(
        title: String,
        body: String,
        source: OrderInputSource,
        platformOverride: DeliveryPlatform
    ): ParsedOrderDraft? {
        val combined = listOf(title.trim(), body.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (combined.isBlank()) return null

        val base = OrderOcrParser.parseOrderText(combined, source)
        val hasRelevantKeyword = listOf(
            "งาน", "ออเดอร์", "ค่าส่ง", "รายได้", "รับ", "ส่ง", "กม", "บาท", "฿",
            "order", "job", "fare", "earn", "pickup", "drop", "km", "thb"
        ).any { combined.lowercase(Locale.ROOT).contains(it) }

        if (base.payoutBaht == null && base.distanceKm == null && !hasRelevantKeyword) {
            return null
        }

        val resolvedPickup = base.pickupName ?: extractFallbackLocationLabel(title, body)
        return base.copy(
            platformHint = platformOverride.displayNameTh,
            pickupName = resolvedPickup
        )
    }

    private fun extractFallbackLocationLabel(title: String, body: String): String? {
        val inlineMerchantRegex = Regex("""(ร้าน[^\n฿0-9]{2,36}?)(?=\s*(?:฿|THB|บาท|ค่าส่ง|รายได้|ไปรับ|ระยะ|รวม|[0-9]+(?:\.[0-9]+)?\s*(?:กม|km|นาที|min)|$))""")
        inlineMerchantRegex.find("$title\n$body")?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.length >= 3 }
            ?.let { return it }

        val platformHeaderRegex = Regex("""^(?:line\s*man|lineman|grab\s*food|grabfood|grab|shopee\s*food|shopeefood|robinhood|งานใหม่|ออเดอร์ใหม่)[:：\-\s]*""", RegexOption.IGNORE_CASE)
        val telemetrySuffixRegex = Regex("""(?:ค่าส่ง|รายได้|ยอดรับ|รวม|ระยะ(?:ทาง)?|ไปรับ|ถึงร้าน)?\s*(?:฿|THB)?\s*[0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?\s*(?:฿|THB|บาท|กม\.?|km|ม\.|เมตร|นาที|mins?|minutes?)\b.*$""", RegexOption.IGNORE_CASE)

        body.lines().forEach { line ->
            val cleaned = line.trim()
                .replace(platformHeaderRegex, "")
                .replace(telemetrySuffixRegex, "")
                .trim()
            if (cleaned.length in 3..48 && !isPurePlatformHeader(cleaned) && !cleaned.all { it.isDigit() || it == '.' }) {
                return cleaned
            }
        }

        val cleanedTitle = title.trim()
            .replace(platformHeaderRegex, "")
            .trim()
        return cleanedTitle.takeIf { it.length in 3..48 && !isPurePlatformHeader(it) }
    }

    private fun isPurePlatformHeader(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        return lower in setOf(
            "line man", "lineman", "grab", "grabfood", "grab food",
            "shopeefood", "shopee food", "robinhood", "งานใหม่", "ออเดอร์ใหม่"
        )
    }
}

object LineManAdapter : BaseDeliveryPlatformAdapter() {
    override val platform: DeliveryPlatform = DeliveryPlatform.LINE_MAN

    override fun supportsPackage(packageName: String): Boolean {
        return super.supportsPackage(packageName) ||
            packageName.lowercase(Locale.ROOT).contains("lineman")
    }

    override fun extractDraft(
        title: String,
        body: String,
        source: OrderInputSource
    ): ParsedOrderDraft? = parseUnified(title, body, source, DeliveryPlatform.LINE_MAN)
}

object GrabAdapter : BaseDeliveryPlatformAdapter() {
    override val platform: DeliveryPlatform = DeliveryPlatform.GRAB

    override fun supportsPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return super.supportsPackage(packageName) ||
            (lower.contains("grab") && (lower.contains("driver") || lower.contains("partner")))
    }

    override fun extractDraft(
        title: String,
        body: String,
        source: OrderInputSource
    ): ParsedOrderDraft? = parseUnified(title, body, source, DeliveryPlatform.GRAB)
}

object ShopeeFoodAdapter : BaseDeliveryPlatformAdapter() {
    override val platform: DeliveryPlatform = DeliveryPlatform.SHOPEE_FOOD

    override fun supportsPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return super.supportsPackage(packageName) ||
            (lower.contains("shopee") && (lower.contains("food") || lower.contains("driver") || lower.contains("rider")))
    }

    override fun extractDraft(
        title: String,
        body: String,
        source: OrderInputSource
    ): ParsedOrderDraft? = parseUnified(title, body, source, DeliveryPlatform.SHOPEE_FOOD)
}

object GenericDeliveryAdapter : BaseDeliveryPlatformAdapter() {
    override val platform: DeliveryPlatform = DeliveryPlatform.OTHER

    override fun supportsPackage(packageName: String): Boolean = false

    override fun extractDraft(
        title: String,
        body: String,
        source: OrderInputSource
    ): ParsedOrderDraft? {
        val combined = "$title\n$body"
        val detected = DeliveryPlatform.fromHintText(combined)
        return parseUnified(title, body, source, detected)
    }
}

object DeliveryAdapterRegistry {
    val primaryAdapters: List<DeliveryPlatformAdapter> = listOf(
        LineManAdapter,
        GrabAdapter,
        ShopeeFoodAdapter
    )

    fun resolveForPackage(packageName: String): DeliveryPlatformAdapter? {
        return primaryAdapters.firstOrNull { it.supportsPackage(packageName) }
    }

    fun resolveForText(rawText: String): DeliveryPlatformAdapter {
        val hinted = DeliveryPlatform.fromHintText(rawText)
        return primaryAdapters.firstOrNull { it.platform == hinted } ?: GenericDeliveryAdapter
    }
}
