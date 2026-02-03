package com.example.nanomaps.ui.map

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.nanomaps.R
import com.example.nanomaps.data.FantasyLocation
import com.example.nanomaps.data.FantasyMap
import com.example.nanomaps.data.MapMode
import com.example.nanomaps.databinding.FragmentMapBinding
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URL
import kotlin.math.atan2
import kotlin.math.sqrt

enum class MapLayer {
    STREET, SATELLITE
}

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()

    private var marker: Marker? = null
    private var directionLine: Polyline? = null
    private var isDragging = false
    private var dragStartPoint: GeoPoint? = null
    private var currentLayer = MapLayer.STREET

    private var fantasyMarkerPosition: PointF? = null
    private var fantasyDirectionEnd: PointF? = null
    private var isFantasyDragging = false
    private var fantasyDragStartPoint: PointF? = null

    private val fantasyOverlayPaint = Paint().apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val fantasyLinePaint = Paint().apply {
        color = Color.parseColor("#EA4335")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val satelliteTileSource: OnlineTileSourceBase by lazy {
        object : XYTileSource(
            "Esri.WorldImagery",
            0, 19, 256, ".jpg",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "${baseUrl}$zoom/$y/$x"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        setupModeToggle()
        setupLayerToggle()
        setupSearch()
        setupFantasyMapView()
        setupGenerateButton()
        setupActionButtons()
        observeViewModel()
        restoreMapState()
    }

    private fun setupModeToggle() {
        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds.first()) {
                    R.id.chip_real_world -> {
                        viewModel.setMapMode(MapMode.REAL_WORLD)
                        showRealWorldMode()
                    }
                    R.id.chip_fantasy -> {
                        viewModel.setMapMode(MapMode.FANTASY)
                        showFantasyMode()
                    }
                }
            }
        }
    }

    private fun showRealWorldMode() {
        binding.mapView.visibility = View.VISIBLE
        binding.layerChipGroup.visibility = View.VISIBLE
        binding.fantasyMapView.visibility = View.GONE
        binding.fantasyOverlay.visibility = View.GONE
        binding.fantasyPlaceholder.visibility = View.GONE
        binding.fabSelectFantasyMap.visibility = View.GONE
        binding.searchLayout.visibility = View.VISIBLE
        binding.mapInstructions.text = getString(R.string.tap_drag_instruction)
    }

    private fun showFantasyMode() {
        binding.mapView.visibility = View.GONE
        binding.layerChipGroup.visibility = View.GONE
        binding.searchLayout.visibility = View.GONE
        binding.fabSelectFantasyMap.visibility = View.VISIBLE
        binding.mapInstructions.text = getString(R.string.tap_drag_fantasy_instruction)

        val activeMap = viewModel.activeFantasyMap.value
        val bitmap = viewModel.fantasyMapBitmap.value

        if (activeMap != null && bitmap != null) {
            binding.fantasyMapView.visibility = View.VISIBLE
            binding.fantasyOverlay.visibility = View.VISIBLE
            binding.fantasyPlaceholder.visibility = View.GONE
            binding.fantasyMapView.setImageBitmap(bitmap)
        } else {
            binding.fantasyMapView.visibility = View.GONE
            binding.fantasyOverlay.visibility = View.GONE
            binding.fantasyPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun setupFantasyMapView() {
        binding.fabSelectFantasyMap.setOnClickListener {
            showFantasyMapSelectionDialog()
        }

        binding.fantasyOverlay.photoView = binding.fantasyMapView

        binding.fantasyOverlay.setOnTouchListener { _, event ->
            handleFantasyTouch(event)
        }

        binding.fantasyMapView.setOnMatrixChangeListener {
            binding.fantasyOverlay.invalidate()
        }
    }

    private fun handleFantasyTouch(event: MotionEvent): Boolean {
        val photoView = binding.fantasyMapView
        val bitmap = viewModel.fantasyMapBitmap.value ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val imagePoint = getImagePointFromTouch(photoView, event.x, event.y, bitmap)
                if (imagePoint != null) {
                    if (fantasyMarkerPosition != null && isNearFantasyMarker(event.x, event.y)) {
                        isFantasyDragging = true
                        fantasyDragStartPoint = fantasyMarkerPosition
                        showDirectionIndicator()
                        return true
                    } else {
                        placeFantasyMarker(imagePoint, event.x, event.y)
                        isFantasyDragging = true
                        fantasyDragStartPoint = imagePoint
                        showDirectionIndicator()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isFantasyDragging && fantasyDragStartPoint != null) {
                    val imagePoint = getImagePointFromTouch(photoView, event.x, event.y, bitmap)
                    if (imagePoint != null) {
                        updateFantasyDirectionLine(fantasyDragStartPoint!!, imagePoint, event.x, event.y)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isFantasyDragging && fantasyDragStartPoint != null) {
                    isFantasyDragging = false
                    val imagePoint = getImagePointFromTouch(photoView, event.x, event.y, bitmap)
                    if (imagePoint != null) {
                        finalizeFantasyDirection(fantasyDragStartPoint!!, imagePoint)
                    }
                    hideDirectionIndicator()
                    return true
                }
            }
        }
        return false
    }

    private fun getImagePointFromTouch(photoView: PhotoView, touchX: Float, touchY: Float, bitmap: Bitmap): PointF? {
        val drawable = photoView.drawable ?: return null
        val matrix = photoView.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scaleX = values[android.graphics.Matrix.MSCALE_X]
        val scaleY = values[android.graphics.Matrix.MSCALE_Y]
        val transX = values[android.graphics.Matrix.MTRANS_X]
        val transY = values[android.graphics.Matrix.MTRANS_Y]

        val imageX = (touchX - transX) / scaleX
        val imageY = (touchY - transY) / scaleY

        if (imageX < 0 || imageX > drawable.intrinsicWidth || imageY < 0 || imageY > drawable.intrinsicHeight) {
            return null
        }

        val xPercent = imageX / drawable.intrinsicWidth
        val yPercent = imageY / drawable.intrinsicHeight

        return PointF(xPercent, yPercent)
    }

    private fun isNearFantasyMarker(touchX: Float, touchY: Float): Boolean {
        val markerScreenPos = getFantasyMarkerScreenPosition() ?: return false
        val distance = sqrt(
            (touchX - markerScreenPos.x) * (touchX - markerScreenPos.x) +
            (touchY - markerScreenPos.y) * (touchY - markerScreenPos.y)
        )
        return distance < 80f
    }

    private fun getFantasyMarkerScreenPosition(): PointF? {
        val markerPos = fantasyMarkerPosition ?: return null
        val photoView = binding.fantasyMapView
        val drawable = photoView.drawable ?: return null
        val matrix = photoView.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scaleX = values[android.graphics.Matrix.MSCALE_X]
        val scaleY = values[android.graphics.Matrix.MSCALE_Y]
        val transX = values[android.graphics.Matrix.MTRANS_X]
        val transY = values[android.graphics.Matrix.MTRANS_Y]

        val screenX = markerPos.x * drawable.intrinsicWidth * scaleX + transX
        val screenY = markerPos.y * drawable.intrinsicHeight * scaleY + transY

        return PointF(screenX, screenY)
    }

    private fun placeFantasyMarker(imagePoint: PointF, screenX: Float, screenY: Float) {
        fantasyMarkerPosition = imagePoint
        fantasyDirectionEnd = null

        viewModel.setFantasyLocation(FantasyLocation(imagePoint.x, imagePoint.y))
        binding.fantasyOverlay.photoView = binding.fantasyMapView
        binding.fantasyOverlay.updateMarker(imagePoint)
        binding.fantasyOverlay.updateDirection(null)
        showSnackbar(getString(R.string.direction_not_set))
    }

    private fun updateFantasyDirectionLine(start: PointF, end: PointF, screenX: Float, screenY: Float) {
        fantasyDirectionEnd = end
        binding.fantasyOverlay.updateDirection(end)

        val direction = calculateFantasyDirection(start, end)
        binding.directionText.text = "${direction}° (${viewModel.getDirectionName(direction)})"
        updateDirectionIndicatorText(direction)
    }

    private fun finalizeFantasyDirection(start: PointF, end: PointF) {
        val direction = calculateFantasyDirection(start, end)
        viewModel.setFantasyDirection(direction)
        fantasyDirectionEnd = end
        binding.fantasyOverlay.updateDirection(end)
    }

    private fun calculateFantasyDirection(start: PointF, end: PointF): Int {
        val dx = end.x - start.x
        val dy = start.y - end.y
        var angle = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble()))
        if (angle < 0) angle += 360
        return angle.toInt()
    }

    private fun showFantasyMapSelectionDialog() {
        val maps = viewModel.getFantasyMaps()
        if (maps.isEmpty()) {
            showSnackbar(getString(R.string.no_fantasy_maps))
            return
        }

        val mapNames = maps.map { it.name }.toTypedArray()
        val activeMapId = viewModel.activeFantasyMap.value?.id
        val selectedIndex = maps.indexOfFirst { it.id == activeMapId }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_fantasy_map)
            .setSingleChoiceItems(mapNames, selectedIndex) { dialog, which ->
                viewModel.setActiveFantasyMap(maps[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupLayerToggle() {
        if (viewModel.isSatelliteLayer) {
            binding.chipSatellite.isChecked = true
            currentLayer = MapLayer.SATELLITE
            binding.mapView.setTileSource(satelliteTileSource)
        } else {
            binding.chipStreet.isChecked = true
            currentLayer = MapLayer.STREET
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        }

        binding.layerChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds.first()) {
                    R.id.chip_street -> {
                        currentLayer = MapLayer.STREET
                        viewModel.isSatelliteLayer = false
                        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
                    }
                    R.id.chip_satellite -> {
                        currentLayer = MapLayer.SATELLITE
                        viewModel.isSatelliteLayer = true
                        binding.mapView.setTileSource(satelliteTileSource)
                    }
                }
                binding.mapView.invalidate()
            }
        }
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(viewModel.mapZoom)
            controller.setCenter(viewModel.mapCenter)

            val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    placeMarker(p)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean {
                    viewModel.clearSelection()
                    clearMapOverlays()
                    return true
                }
            })
            overlays.add(0, mapEventsOverlay)

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val point = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                        if (marker != null && isNearMarker(point)) {
                            isDragging = true
                            dragStartPoint = viewModel.location.value
                            showDirectionIndicator()
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging && dragStartPoint != null) {
                            val point = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                            updateDirectionLine(dragStartPoint!!, point)
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging && dragStartPoint != null) {
                            isDragging = false
                            val point = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                            finalizeDirection(dragStartPoint!!, point)
                            hideDirectionIndicator()
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
    }

    private fun showDirectionIndicator() {
        binding.directionIndicatorInclude.directionIndicator.fadeIn()
    }

    private fun hideDirectionIndicator() {
        binding.directionIndicatorInclude.directionIndicator.fadeOut()
    }

    private fun updateDirectionIndicatorText(direction: Int) {
        binding.directionIndicatorInclude.directionIndicatorText.text =
            "${direction}° ${viewModel.getDirectionName(direction)}"
    }

    private fun isNearMarker(point: GeoPoint): Boolean {
        val markerPos = viewModel.location.value ?: return false
        val distance = markerPos.distanceToAsDouble(point)
        val zoomLevel = binding.mapView.zoomLevelDouble
        val threshold = 50000 / Math.pow(2.0, zoomLevel)
        return distance < threshold
    }

    private fun placeMarker(point: GeoPoint) {
        clearMapOverlays()

        marker = Marker(binding.mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createMarkerIcon()
            title = "Selected Location"
        }
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()

        viewModel.setLocation(point)
        isDragging = true
        dragStartPoint = point

        showSnackbar(getString(R.string.direction_not_set))
    }

    private fun createMarkerIcon(): Drawable {
        val drawable = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mylocation)!!
        val wrappedDrawable = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrappedDrawable, Color.parseColor("#4285F4"))
        return wrappedDrawable
    }

    private fun updateDirectionLine(start: GeoPoint, end: GeoPoint) {
        directionLine?.let { binding.mapView.overlays.remove(it) }

        directionLine = Polyline().apply {
            addPoint(start)
            addPoint(end)
            outlinePaint.apply {
                color = Color.parseColor("#EA4335")
                strokeWidth = 8f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        }
        binding.mapView.overlays.add(directionLine)
        binding.mapView.invalidate()

        val direction = calculateDirection(start, end)
        binding.directionText.text = "${direction}° (${viewModel.getDirectionName(direction)})"
        updateDirectionIndicatorText(direction)
    }

    private fun finalizeDirection(start: GeoPoint, end: GeoPoint) {
        val direction = calculateDirection(start, end)
        viewModel.setDirection(direction)
    }

    private fun calculateDirection(start: GeoPoint, end: GeoPoint): Int {
        val dLng = end.longitude - start.longitude
        val dLat = end.latitude - start.latitude
        var angle = Math.toDegrees(atan2(dLng, dLat))
        if (angle < 0) angle += 360
        return angle.toInt()
    }

    private fun clearMapOverlays() {
        marker?.let { binding.mapView.overlays.remove(it) }
        directionLine?.let { binding.mapView.overlays.remove(it) }
        marker = null
        directionLine = null
        binding.mapView.invalidate()
    }

    private fun setupSearch() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.searchLayout.setEndIconOnClickListener {
            performSearch()
        }
    }

    private fun performSearch() {
        val query = binding.searchInput.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL(
                        "https://nominatim.openstreetmap.org/search?format=json&q=${
                            java.net.URLEncoder.encode(query, "UTF-8")
                        }&limit=1"
                    )
                    val connection = url.openConnection()
                    connection.setRequestProperty("User-Agent", "NanoMaps Android App")
                    connection.inputStream.bufferedReader().readText()
                }

                val jsonArray = JSONArray(result)
                if (jsonArray.length() > 0) {
                    val location = jsonArray.getJSONObject(0)
                    val lat = location.getDouble("lat")
                    val lon = location.getDouble("lon")
                    val geoPoint = GeoPoint(lat, lon)

                    binding.mapView.controller.animateTo(geoPoint)
                    binding.mapView.controller.setZoom(16.0)

                    showSnackbar("Found location")
                } else {
                    showSnackbar("Location not found")
                }
            } catch (e: Exception) {
                showSnackbar("Search failed: ${e.message}")
            }
        }
    }

    private fun setupGenerateButton() {
        binding.generateButton.setOnClickListener {
            if (!viewModel.checkApiKeyExists()) {
                showSnackbar(getString(R.string.error_no_api_key))
                return@setOnClickListener
            }

            val customPrompt = binding.customPromptInput.text?.toString()?.trim()

            if (viewModel.mapMode.value == MapMode.FANTASY) {
                val fantasyBitmap = viewModel.fantasyMapBitmap.value
                if (fantasyBitmap == null) {
                    showSnackbar(getString(R.string.requirement_select_fantasy_map))
                    return@setOnClickListener
                }
                viewModel.generateFantasy(fantasyBitmap, customPrompt)
            } else {
                val mapBitmap = captureMapView()
                val isSatellite = currentLayer == MapLayer.SATELLITE
                viewModel.generate(mapBitmap, customPrompt, isSatellite)
            }
        }
    }

    private fun setupActionButtons() {
        binding.viewFullButton.setOnClickListener {
            showFullscreenImage()
        }

        binding.saveButton.setOnClickListener {
            viewModel.saveCurrentImage()
        }

        binding.resultImage.setOnClickListener {
            showFullscreenImage()
        }

        binding.loadingOverlayInclude.cancelButton.setOnClickListener {
            viewModel.cancelGeneration()
        }
    }

    private fun showFullscreenImage() {
        val bitmap = viewModel.getCurrentBitmap() ?: return

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_fullscreen_image)

        val imageView = dialog.findViewById<ImageView>(R.id.fullscreen_image)
        imageView.setImageBitmap(bitmap)

        dialog.findViewById<MaterialButton>(R.id.dialog_save_button).setOnClickListener {
            viewModel.saveCurrentImage()
        }

        dialog.findViewById<MaterialButton>(R.id.dialog_close_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun captureMapView(): Bitmap {
        directionLine?.let { binding.mapView.overlays.remove(it) }
        marker?.let { binding.mapView.overlays.remove(it) }
        binding.mapView.invalidate()

        val bitmap = Bitmap.createBitmap(
            binding.mapView.width,
            binding.mapView.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        binding.mapView.draw(canvas)

        marker?.let { binding.mapView.overlays.add(it) }
        directionLine?.let { binding.mapView.overlays.add(it) }
        binding.mapView.invalidate()

        return bitmap
    }

    private fun observeViewModel() {
        viewModel.mapMode.observe(viewLifecycleOwner) { mode ->
            when (mode) {
                MapMode.REAL_WORLD -> {
                    binding.chipRealWorld.isChecked = true
                    showRealWorldMode()
                }
                MapMode.FANTASY -> {
                    binding.chipFantasy.isChecked = true
                    showFantasyMode()
                }
                null -> {}
            }
        }

        viewModel.activeFantasyMap.observe(viewLifecycleOwner) { map ->
            if (viewModel.mapMode.value == MapMode.FANTASY) {
                showFantasyMode()
            }
        }

        viewModel.fantasyMapBitmap.observe(viewLifecycleOwner) { bitmap ->
            if (viewModel.mapMode.value == MapMode.FANTASY && bitmap != null) {
                binding.fantasyMapView.setImageBitmap(bitmap)
                binding.fantasyMapView.visibility = View.VISIBLE
                binding.fantasyOverlay.visibility = View.VISIBLE
                binding.fantasyPlaceholder.visibility = View.GONE

                fantasyMarkerPosition = null
                fantasyDirectionEnd = null
                binding.fantasyOverlay.clearOverlay()
            }
        }

        viewModel.location.observe(viewLifecycleOwner) { location ->
            if (viewModel.mapMode.value == MapMode.REAL_WORLD) {
                if (location != null) {
                    binding.locationText.text = String.format("%.6f, %.6f", location.latitude, location.longitude)
                } else {
                    binding.locationText.text = getString(R.string.location_not_selected)
                    binding.directionText.text = getString(R.string.direction_not_set)
                }
            }
        }

        viewModel.fantasyLocation.observe(viewLifecycleOwner) { location ->
            if (viewModel.mapMode.value == MapMode.FANTASY) {
                if (location != null) {
                    binding.locationText.text = getString(
                        R.string.fantasy_position_format,
                        location.xPercent * 100,
                        location.yPercent * 100
                    )
                } else {
                    binding.locationText.text = getString(R.string.location_not_selected)
                    binding.directionText.text = getString(R.string.direction_not_set)
                }
            }
        }

        viewModel.direction.observe(viewLifecycleOwner) { direction ->
            if (viewModel.mapMode.value == MapMode.REAL_WORLD && direction != null) {
                binding.directionText.text = "${direction}° (${viewModel.getDirectionName(direction)})"
            }
        }

        viewModel.fantasyDirection.observe(viewLifecycleOwner) { direction ->
            if (viewModel.mapMode.value == MapMode.FANTASY && direction != null) {
                binding.directionText.text = "${direction}° (${viewModel.getDirectionName(direction)})"
            }
        }

        viewModel.canGenerate.observe(viewLifecycleOwner) { canGenerate ->
            binding.generateButton.isEnabled = canGenerate
        }

        viewModel.requirementHint.observe(viewLifecycleOwner) { hint ->
            val (text, colorAttr) = when (hint) {
                MapViewModel.RequirementHint.LOCATION ->
                    getString(R.string.requirement_select_location) to com.google.android.material.R.attr.colorOnSurfaceVariant
                MapViewModel.RequirementHint.DIRECTION ->
                    getString(R.string.requirement_set_direction) to com.google.android.material.R.attr.colorOnSurfaceVariant
                MapViewModel.RequirementHint.API_KEY ->
                    getString(R.string.requirement_set_api_key) to com.google.android.material.R.attr.colorError
                MapViewModel.RequirementHint.READY ->
                    getString(R.string.ready_to_generate) to com.google.android.material.R.attr.colorPrimary
                MapViewModel.RequirementHint.SELECT_FANTASY_MAP ->
                    getString(R.string.requirement_select_fantasy_map) to com.google.android.material.R.attr.colorOnSurfaceVariant
                MapViewModel.RequirementHint.FANTASY_LOCATION ->
                    getString(R.string.requirement_fantasy_location) to com.google.android.material.R.attr.colorOnSurfaceVariant
                MapViewModel.RequirementHint.FANTASY_DIRECTION ->
                    getString(R.string.requirement_fantasy_direction) to com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            binding.requirementHint.text = text
            binding.requirementHint.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(requireContext(), colorAttr, Color.GRAY)
            )
        }

        viewModel.generationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MapViewModel.GenerationState.Idle -> {
                    binding.loadingOverlayInclude.loadingOverlay.fadeOut()
                    binding.placeholderLayout.isVisible = true
                    binding.resultImage.isVisible = false
                    binding.actionButtonsLayout.isVisible = false
                }
                is MapViewModel.GenerationState.Loading -> {
                    binding.loadingOverlayInclude.loadingOverlay.fadeIn()
                    binding.placeholderLayout.isVisible = false
                    binding.generateButton.isEnabled = false
                }
                is MapViewModel.GenerationState.Success -> {
                    binding.loadingOverlayInclude.loadingOverlay.fadeOut()
                    binding.placeholderLayout.isVisible = false
                    binding.resultImage.apply {
                        setImageBitmap(state.bitmap)
                        isVisible = true
                        scaleIn()
                    }
                    binding.actionButtonsLayout.apply {
                        isVisible = true
                        slideUp()
                    }
                    binding.generateButton.isEnabled = true
                    showSnackbar(getString(R.string.success_generated))
                }
                is MapViewModel.GenerationState.Error -> {
                    binding.loadingOverlayInclude.loadingOverlay.fadeOut()
                    binding.placeholderLayout.isVisible = true
                    binding.generateButton.isEnabled = true
                    showSnackbar(state.message)
                }
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is MapViewModel.SaveResult.Success -> {
                    showSnackbar(getString(R.string.image_saved))
                    viewModel.clearSaveResult()
                }
                is MapViewModel.SaveResult.Failed -> {
                    showSnackbar(getString(R.string.image_save_failed))
                    viewModel.clearSaveResult()
                }
                null -> {}
            }
        }
    }

    private fun restoreMapState() {
        val location = viewModel.location.value
        val direction = viewModel.direction.value

        if (location != null) {
            marker = Marker(binding.mapView).apply {
                position = location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createMarkerIcon()
                title = "Selected Location"
            }
            binding.mapView.overlays.add(marker)

            if (direction != null) {
                val endLat = location.latitude + 0.001 * kotlin.math.cos(Math.toRadians(direction.toDouble()))
                val endLng = location.longitude + 0.001 * kotlin.math.sin(Math.toRadians(direction.toDouble()))
                val endPoint = GeoPoint(endLat, endLng)

                directionLine = Polyline().apply {
                    addPoint(location)
                    addPoint(endPoint)
                    outlinePaint.apply {
                        color = Color.parseColor("#EA4335")
                        strokeWidth = 8f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                    }
                }
                binding.mapView.overlays.add(directionLine)
            }

            binding.mapView.invalidate()
        }
    }

    private fun showSnackbar(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun View.fadeIn(duration: Long = 300) {
        if (!isVisible) {
            alpha = 0f
            isVisible = true
        }
        animate()
            .alpha(1f)
            .setDuration(duration)
            .start()
    }

    private fun View.fadeOut(duration: Long = 200) {
        animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction { isVisible = false }
            .start()
    }

    private fun View.scaleIn() {
        val animation = AnimationUtils.loadAnimation(context, R.anim.scale_in)
        startAnimation(animation)
    }

    private fun View.slideUp() {
        val animation = AnimationUtils.loadAnimation(context, R.anim.slide_up)
        startAnimation(animation)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        viewModel.refreshCanGenerate()
        viewModel.refreshFantasyMaps()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        viewModel.setMapState(
            binding.mapView.mapCenter as GeoPoint,
            binding.mapView.zoomLevelDouble
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
