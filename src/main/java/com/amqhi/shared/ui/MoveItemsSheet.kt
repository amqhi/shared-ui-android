/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.shared.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.amqhi.shared.core.ItemSummary
import com.amqhi.shared.core.ItemType
import com.amqhi.shared.core.SpecialFolder
import com.amqhi.shared.ui.databinding.SheetMoveBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.UUID

interface MoveItemsHost {
    val surfaceColor: Int
    val iconButtonColor: Int
    val textColorPrimary: Int
    val onPrimaryColor: Int
    val primaryColor: Int
    fun moveItems(ids: List<UUID>, sourceId: UUID, destinationId: UUID)
    fun onMoveFinished()
}

class MoveItemsSheet : BottomSheetDialogFragment(), FolderBrowseHost {
    private var _binding: SheetMoveBinding? = null
    private val binding get() = _binding!!

    private val sourceId: UUID
        get() = UUID.fromString(requireArguments().getString(ARG_SOURCE_ID))

    private val rootTitle: String
        get() = requireArguments().getString(ARG_ROOT_TITLE).orEmpty()

    private val selectedIds: List<UUID>
        get() = requireArguments().getStringArrayList(ARG_SELECTED_IDS)
            .orEmpty()
            .map(UUID::fromString)

    override fun getTheme(): Int = R.style.Theme_Anemoneo_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetMoveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? MoveItemsHost)?.let { host ->
            binding.background.backgroundTintList = ColorStateList.valueOf(host.surfaceColor)
            binding.backButton.setTextColor(host.iconButtonColor)
            binding.title.setTextColor(host.textColorPrimary)
            binding.moveHere.setTextColor(host.onPrimaryColor)
            (binding.moveHere.background?.mutate() as? GradientDrawable)?.setColor(host.primaryColor)
        }
        binding.backButton.setOnClickListener {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            }
        }
        binding.moveHere.setOnClickListener { moveHere() }
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.moveFolderContainer,
                    FilesFragment.newPickerInstance(
                        SpecialFolder.HOME,
                        rootTitle,
                        hiddenFolderIds()
                    )
                )
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun browseFolder(folderId: UUID, title: String) {
        childFragmentManager.beginTransaction()
            .replace(
                R.id.moveFolderContainer,
                FilesFragment.newPickerInstance(folderId, title, hiddenFolderIds())
            )
            .addToBackStack(folderId.toString())
            .commit()
    }

    override fun onBrowseFolderShown(folderId: UUID, title: String) {
        val content = _binding ?: return
        content.title.text = title
        content.backButton.isVisible = childFragmentManager.backStackEntryCount > 0
        content.moveHere.isVisible = folderId != sourceId
    }

    private fun hiddenFolderIds(): Set<UUID> {
        return requireArguments().getStringArrayList(ARG_HIDDEN_IDS)
            .orEmpty()
            .map(UUID::fromString)
            .toSet()
    }

    private fun moveHere() {
        val destination = (childFragmentManager.findFragmentById(R.id.moveFolderContainer) as? FilesFragment)
            ?.folderId
            ?: return
        val host = activity as? MoveItemsHost ?: return
        val ids = selectedIds
        host.moveItems(ids, sourceId, destination)
        host.onMoveFinished()
        dismiss()
    }

    companion object {
        private const val ARG_SOURCE_ID = "source_id"
        private const val ARG_ROOT_TITLE = "root_title"
        private const val ARG_SELECTED_IDS = "selected_ids"
        private const val ARG_HIDDEN_IDS = "hidden_ids"

        fun newInstance(
            sourceId: UUID,
            selected: List<ItemSummary>,
            rootTitle: String,
        ) = MoveItemsSheet().apply {
            val hiddenIds = buildList {
                add(sourceId.toString())
                add(SpecialFolder.TRASH.toString())
                selected.filter { it.type == ItemType.FOLDER }.forEach { add(it.id.toString()) }
            }
            arguments = Bundle().apply {
                putString(ARG_SOURCE_ID, sourceId.toString())
                putString(ARG_ROOT_TITLE, rootTitle)
                putStringArrayList(ARG_SELECTED_IDS, ArrayList(selected.map { it.id.toString() }))
                putStringArrayList(ARG_HIDDEN_IDS, ArrayList(hiddenIds))
            }
        }
    }
}
