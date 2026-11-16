package co.znxdnd.echotalk.utils

enum class PerformanceProfile(val displayName: String, val description: String) {
    HIGH(
        "High Performance",
        "Full blur effects, rich shadows, and maximum animation fidelity."
    ),
    BALANCED(
        "Balanced",
        "Optimized combination of visual depth and smooth battery efficiency."
    ),
    LITE(
        "Lite / Battery Saver",
        "Disables blurs, simplifies spring animations, and reduces image resolution."
    ),
    AUTOMATIC(
        "Automatic (Adaptive)",
        "Dynamically adapts based on device memory, battery saver mode, and device thermal state."
    )
}

data class PerformanceMetrics(
    val profile: PerformanceProfile,
    val enableBlurs: Boolean,
    val enableCardShadows: Boolean,
    val enableComplexSprings: Boolean,
    val imageScaleFactor: Float,
    val memoryUsageMb: Long,
    val maxHeapMb: Long,
    val isLowMemory: Boolean
)
