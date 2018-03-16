package kotcity.ui.layers

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.util.Duration
import kotcity.data.BlockCoordinate
import kotcity.data.CityMap
import kotcity.data.Location
import kotcity.data.Zot
import kotcity.ui.ResizableCanvas
import kotcity.ui.map.CityRenderer
import kotcity.ui.sprites.ZotSpriteLoader
import kotcity.util.Debuggable
import kotcity.util.randomElement
import kotcity.util.randomElements
import java.util.concurrent.TimeUnit

class ZotRenderer(private val cityMap: CityMap, private val cityRenderer: CityRenderer, private val zotCanvas: ResizableCanvas): Debuggable {

    private var offsetTimeline: Timeline
    private var degree = 0.0
    private var lastCalculatedTime = System.currentTimeMillis()
    private var locationsWithZots = emptyList<Location>()
    private val zotRecalcInterval = 15000

    init {

        this.offsetTimeline = Timeline(KeyFrame(Duration.millis(50.0), EventHandler {
            degree += 5
            if (degree >= 360) {
                degree = 0.0
            }
        }))

        offsetTimeline.cycleCount = Timeline.INDEFINITE
        offsetTimeline.play()
    }

    fun stop() {
        // stop animation thread here...
        offsetTimeline.stop()
    }

    private fun drawZot(image: Image, g2d: GraphicsContext, location: Location) {
        val coordinate = location.coordinate
        val tx = coordinate.x - cityRenderer.blockOffsetX
        val ty = coordinate.y - cityRenderer.blockOffsetY
        val blockSize = cityRenderer.blockSize()
        // gotta fill that background too...

        val halfBuildingWidth = if (location.building.width > 1) {
            ((location.building.width * blockSize) / 2.0) / 2.0
        } else {
            0.0
        }


        val y = (ty - 1) * blockSize + ((Math.sin(Math.toRadians(degree)) * (blockSize * 0.1)))
        drawOutline(g2d, tx, blockSize, y, halfBuildingWidth)

        g2d.drawImage(image, (tx * blockSize) + halfBuildingWidth, y)
    }

    private fun drawOutline(g2d: GraphicsContext, tx: Double, blockSize: Double, y: Double, halfBuildingWidth: Double) {
        g2d.fill = Color.WHITE

        val quarterBlock = blockSize * 0.25
        val halfBlock = blockSize * 0.5

        val x = (tx * blockSize) - quarterBlock
        val py = y - quarterBlock
        g2d.fillOval(x + halfBuildingWidth, py, blockSize + halfBlock, blockSize + halfBlock)

        g2d.stroke = Color.RED
        g2d.strokeOval(x + halfBuildingWidth, py, blockSize + halfBlock, blockSize + halfBlock)
    }

    // OK, here is what we want to do...
    // if delta < 5000msec... just return the list we already had...
    // otherwise calculate it and then bring it back...
    private fun cachedLocationsWithZots(visibleBlockRange: Pair<BlockCoordinate, BlockCoordinate>): List<Location> {
        val delta = System.currentTimeMillis() - lastCalculatedTime
        if (delta < zotRecalcInterval) {
            return locationsWithZots
        }

        locationsWithZots = randomBuildingsWithZots(visibleBlockRange).randomElements(50) ?: emptyList()
        lastCalculatedTime = System.currentTimeMillis()
        return locationsWithZots
    }

    fun render() {
        val gc = zotCanvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, zotCanvas.width, zotCanvas.height)
        gc.fill = Color.AQUAMARINE

        // ok let's get all buildings with zots now...
        visibleBlockRange?.let { visibleBlockRange ->
            val locationsWithZots = cachedLocationsWithZots(visibleBlockRange)
            locationsWithZots?.forEach { location ->
                // TODO: we gotta get different zots every once in a while...
                val randomZot: Zot? = randomZot(location)
                if (randomZot != null) {
                    val image = ZotSpriteLoader.spriteForZot(randomZot, cityRenderer.blockSize(), cityRenderer.blockSize())
                    if (image != null) {
                        drawZot(image, gc, location)
                    }
                }
            }
        }
    }

    // OK we need a cache here so we only shoot back a few buildings
    private fun randomBuildingsWithZots(visibleBlockRange: Pair<BlockCoordinate, BlockCoordinate>): List<Location> {
        return cityMap.locationsIn(visibleBlockRange.first, visibleBlockRange.second).filter { it.building.zots.isNotEmpty() }
    }


    private var zotForBuildingCache: LoadingCache<Location, Zot?> =  Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build<Location, Zot?> { key -> key.building.zots.randomElement() }

    private fun randomZot(location: Location): Zot? {
        return zotForBuildingCache[location]
    }

    override var debug: Boolean = true
    var visibleBlockRange: Pair<BlockCoordinate, BlockCoordinate>? = null
}