/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.shared.ui

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.amqhi.shared.core.ItemSummary
import com.amqhi.shared.core.SpecialFolder
import com.amqhi.shared.ui.databinding.ActivitySelectFilesBinding
import java.util.UUID

abstract class SelectFilesActivityBase : AppCompatActivity(), FilesHost {
    protected open val rootFolderId: UUID = SpecialFolder.HOME
    protected open val rootTitle: String = getString(R.string.files_title)

    protected open val fabBackgroundColor: Int = 0xFFFFFFFF.toInt()
    protected open val fabIconColor: Int = 0xFF2196F3.toInt()
    protected open val fabLabelColor: Int = 0xFF212121.toInt()
    protected open val fabShadow: Boolean = true

    override val gridSpanCount: Int = 3
    override val searchQuery: String = ""

    private lateinit var binding: ActivitySelectFilesBinding
    private var fabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySelectFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        applyFabStyle()
        hideFabRowsUntilLaidOut()
        binding.mainFab.setOnClickListener { toggleFabMenu() }
        binding.folderFab.setOnClickListener { onFolderFabClicked() }
        binding.fileFab.setOnClickListener { onFileFabClicked() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.filesContainer, FilesFragment.newInstance(rootFolderId, rootTitle))
                .commit()
        }
    }

    protected open fun onFolderFabClicked() {}

    protected open fun onFileFabClicked() {}

    private fun applyFabStyle() {
        val elevation = if (fabShadow) 6f * resources.displayMetrics.density else 0f
        listOf(binding.mainFab, binding.folderFab, binding.fileFab).forEach { fab ->
            fab.setTextColor(fabIconColor)
            fab.background?.mutate()?.setTint(fabBackgroundColor)
            fab.elevation = elevation
        }
        listOf(binding.folderFabLabel, binding.fileFabLabel).forEach { label ->
            label.setTextColor(fabLabelColor)
        }
    }

    private fun hideFabRowsUntilLaidOut() {
        hideRowOffscreen(binding.fileFabRow)
        hideRowOffscreen(binding.folderFabRow)
    }

    private fun hideRowOffscreen(row: View) {
        row.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                row.translationX = ((row.parent as View).width - row.left).toFloat()
                row.visibility = View.VISIBLE
                row.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun toggleFabMenu() {
        if (fabMenuOpen) collapseFabMenu() else expandFabMenu()
    }

    private fun expandFabMenu() {
        fabMenuOpen = true
        showFab(binding.folderFabRow, 1000)
        showFab(binding.fileFabRow, 750)
        binding.mainFab.animate().setInterpolator(DecelerateInterpolator(2f)).rotation(-45f).setDuration(750).start()
    }

    private fun collapseFabMenu() {
        if (!fabMenuOpen) return
        fabMenuOpen = false
        hideFab(binding.folderFabRow, 750)
        hideFab(binding.fileFabRow, 1000)
        binding.mainFab.animate().setInterpolator(DecelerateInterpolator(2f)).rotation(0f).setDuration(750).start()
    }

    private fun showFab(row: View, duration: Long) {
        row.animate().cancel()
        row.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun hideFab(row: View, duration: Long) {
        row.animate().cancel()
        val hiddenX = ((row.parent as View).width - row.left).toFloat()
        row.animate()
            .translationX(hiddenX)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    override fun openFolder(folderId: UUID, title: String) {
        val current = supportFragmentManager.findFragmentById(R.id.filesContainer) as? FilesFragment
        if (current?.folderId == folderId) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.filesContainer, FilesFragment.newInstance(folderId, title))
            .addToBackStack(folderId.toString())
            .commit()
    }

    override fun onFolderShown(folderId: UUID, title: String) {}

    override fun onSelectionModeChanged(selecting: Boolean) {}

    override fun openFile(item: ItemSummary) {}

    override fun showItemActions(anchor: View, item: ItemSummary) {}
}
