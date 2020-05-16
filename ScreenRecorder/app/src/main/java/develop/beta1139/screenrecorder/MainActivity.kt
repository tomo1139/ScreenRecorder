package develop.beta1139.screenrecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.snackbar.Snackbar
import develop.beta1139.screenrecorder.databinding.ActivityMainBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
    }
    private val ORIENTATIONS = SparseIntArray()
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjectionCallback: MediaProjectionCallback? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenDensity = 0
    private val mediaRecorder = MediaRecorder()
    private var videoUri: String = ""

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenDensity = metrics.densityDpi

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.toggleButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    + ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    binding.toggleButton.isChecked = false
                    Snackbar.make(binding.rootLayout, "Permissions", Snackbar.LENGTH_INDEFINITE)
                            .setAction("Enable") {
                                ActivityCompat.requestPermissions(this,
                                        arrayOf(
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                Manifest.permission.RECORD_AUDIO
                                        ), REQUEST_PERMISSIONS)
                            }.show()
                } else {
                    ActivityCompat.requestPermissions(this,
                            arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.RECORD_AUDIO
                            ), REQUEST_PERMISSIONS)
                }
            } else {
                toggleScreenShare(binding.toggleButton)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun toggleScreenShare(toggleButton: ToggleButton) {
        if (toggleButton.isChecked) {
            initRecorder()
            recordScreen()
        } else {
            mediaRecorder.stop()
            mediaRecorder.reset()
            stopRecordScreen()

            binding.videoView.visibility = View.VISIBLE
            binding.videoView.setVideoURI(Uri.parse(videoUri))
            binding.videoView.start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun recordScreen() {
        if (mediaProjection == null) {
            startActivityForResult(mediaProjectionManager?.createScreenCaptureIntent(), REQUEST_CODE)
            return
        }
        virtualDisplay = createVirtualDisplay()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createVirtualDisplay(): VirtualDisplay? {
        return mediaProjection?.createVirtualDisplay("MainActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.surface, null, null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initRecorder() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

            videoUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() +
                    StringBuilder("/EDMT_Record_").append(SimpleDateFormat("dd-MM-yyyy-hh_mm_ss", Locale.getDefault()).format(Date())).append("mp4").toString()

            mediaRecorder.setOutputFile(videoUri)
            mediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder.setVideoEncodingBitRate(1500 * 1000)
            mediaRecorder.setVideoFrameRate(30)

            val rotation = windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS.get(rotation + 90)
            mediaRecorder.setOrientationHint(orientation)
            mediaRecorder.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        data ?: return

        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            Toast.makeText(this, "Unk error", Toast.LENGTH_SHORT).show()
            return
        }

        if (resultCode != RESULT_OK) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            binding.toggleButton.isChecked = false
            return
        }

        mediaProjectionCallback = MediaProjectionCallback()
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)
        virtualDisplay = createVirtualDisplay()
        mediaRecorder.start()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && (grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    toggleScreenShare(binding.toggleButton)
                } else {
                    binding.toggleButton.isChecked = false
                    Snackbar.make(binding.rootLayout, "Permissions", Snackbar.LENGTH_INDEFINITE)
                            .setAction("Enable") {
                                ActivityCompat.requestPermissions(this,
                                        arrayOf(
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                Manifest.permission.RECORD_AUDIO
                                        ), REQUEST_PERMISSIONS)
                            }.show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (binding.toggleButton.isChecked) {
                binding.toggleButton.isChecked = false
                mediaRecorder.stop()
                mediaRecorder.reset()
            }
            mediaProjection = null
            stopRecordScreen()
            super.onStop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun stopRecordScreen() {
        virtualDisplay ?: return
        virtualDisplay?.release()
        destroyMediaProjection()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun destroyMediaProjection() {
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    companion object {
        private const val REQUEST_CODE = 1000
        private const val REQUEST_PERMISSIONS = 1001
        private const val DISPLAY_WIDTH = 720
        private const val DISPLAY_HEIGHT = 1280
    }
}
