/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.shared.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.amqhi.shared.core.FileProgressListener
import com.amqhi.shared.core.ItemStatus
import com.amqhi.shared.core.ItemSummary
import com.amqhi.shared.core.ItemType
import com.amqhi.shared.ui.databinding.FragmentFilesBinding
import java.util.UUID

interface FilesHost {
    val gridSpanCount: Int
    val searchQuery: String
    val folderIconBackgroundColor: Int
    val folderIconFrontColor: Int
    fun loadItems(folderId: UUID): List<ItemSummary>
    fun refreshItems()
    fun openFolder(folderId: UUID, title: String)
    fun onFolderShown(folderId: UUID, title: String)
    fun onSelectionModeChanged(selecting: Boolean)
    fun openFile(item: ItemSummary)
    fun showItemActions(anchor: View, item: ItemSummary)
    fun attachTransferListener(listener: FileProgressListener)
    fun detachTransferListener(listener: FileProgressListener)
}

interface FolderBrowseHost {
    fun browseFolder(folderId: UUID, title: String)
    fun onBrowseFolderShown(folderId: UUID, title: String)
}

class FilesFragment : Fragment(), FileProgressListener {
    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FilesAdapter
    private var allItems: List<ItemSummary> = emptyList()
    private var selectionMode = false
    private val selectedItems = linkedMapOf<UUID, ItemSummary>()

    val folderId: UUID
        get() = UUID.fromString(requireNotNull(requireArguments().getString(ARG_FOLDER_ID)))

    val folderTitle: String
        get() = requireArguments().getString(ARG_TITLE).orEmpty()

    private val browseFolders: Boolean
        get() = requireArguments().getBoolean(ARG_BROWSE_FOLDERS)

    private val hiddenFolderIds: Set<UUID>
        get() = requireArguments().getStringArrayList(ARG_HIDDEN_IDS)
            .orEmpty()
            .map(UUID::fromString)
            .toSet()

    val itemCount get() = allItems.size

    private fun host(): FilesHost? = activity as? FilesHost

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        host()?.attachTransferListener(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = host()
        adapter = FilesAdapter(
            onItemClick = { item ->
                if (item.status == ItemStatus.UPLOADING || item.status == ItemStatus.PENDING_UPLOAD) {
                    return@FilesAdapter
                }
                when (item.type) {
                    ItemType.FOLDER -> {
                        if (browseFolders) {
                            (parentFragment as? FolderBrowseHost)?.browseFolder(item.id, item.name)
                        } else {
                            host?.openFolder(item.id, item.name)
                        }
                    }

                    ItemType.FILE -> {
                        if (!browseFolders) host?.openFile(item)
                    }
                }
            },
            onItemLongClick = { anchor, item ->
                if (!browseFolders && item.status != ItemStatus.PENDING_UPLOAD && item.status != ItemStatus.UPLOADING) {
                    host?.showItemActions(anchor, item)
                }
            },
            onItemToggle = { item -> toggleSelection(item) },
            folderBackgroundColor = { host()?.folderIconBackgroundColor ?: 0xFF4AA9FF.toInt() },
            folderFrontColor = { host()?.folderIconFrontColor ?: 0xFF7FC3FE.toInt() },
        )
        adapter.selectionMode = selectionMode
        binding.fileGrid.layoutManager = GridLayoutManager(requireContext(), host?.gridSpanCount ?: 3)
        binding.fileGrid.adapter = adapter
        binding.fileRefresh.isEnabled = !browseFolders
        if (browseFolders) {
            val padding = (8 * resources.displayMetrics.density).toInt()
            binding.fileGrid.setPadding(
                binding.fileGrid.paddingLeft,
                binding.fileGrid.paddingTop,
                binding.fileGrid.paddingRight,
                padding
            )
        }
        binding.fileRefresh.setOnRefreshListener {
            host()?.refreshItems()
        }
        allItems = loadItems()
        applyFilter(if (browseFolders) "" else host?.searchQuery.orEmpty())
    }

    override fun onResume() {
        super.onResume()
        if (browseFolders) {
            (parentFragment as? FolderBrowseHost)?.onBrowseFolderShown(folderId, folderTitle)
            return
        }
        val host = host()
        host?.onFolderShown(folderId, folderTitle)
        host?.onSelectionModeChanged(selectionMode)
    }

    override fun onDestroyView() {
        host()?.detachTransferListener(this)
        super.onDestroyView()
        _binding = null
    }

    fun setSpanCount(span: Int) {
        val grid = _binding?.fileGrid ?: return
        (grid.layoutManager as GridLayoutManager).spanCount = span
        adapter.notifyDataSetChanged()
    }

    fun isInSelectionMode(): Boolean = selectionMode

    fun selectedItems(): List<ItemSummary> = selectedItems.values.toList()

    fun selectAll() {
        if (_binding == null || !selectionMode) return
        adapter.currentList.forEachIndexed { index, it ->
            selectedItems[it.id] = it
            adapter.notifyItemChanged(index, FilesAdapter.SELECTED)
        }
    }

    fun enterSelectionMode() {
        if (_binding == null || selectionMode) return
        selectionMode = true
        syncSelection()
        host()?.onSelectionModeChanged(true)
    }

    fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selectedItems.clear()
        syncSelection()
        host()?.onSelectionModeChanged(false)
    }

    fun reloadItems() {
        if (_binding == null) return
        allItems = loadItems()
        if (selectionMode) {
            val current = allItems.associateBy { it.id }
            val retained = linkedMapOf<UUID, ItemSummary>()
            for (id in selectedItems.keys) {
                val fresh = current[id] ?: continue
                retained[id] = fresh
            }
            selectedItems.clear()
            selectedItems.putAll(retained)
        }
        syncSelection()
        applyFilter(if (browseFolders) "" else host()?.searchQuery.orEmpty())
    }

    fun setRefreshing(refreshing: Boolean) {
        _binding?.fileRefresh?.isRefreshing = refreshing
    }

    fun applyFilter(query: String) {
        val trimmed = query.trim()
        adapter.submitList(
            if (trimmed.isEmpty()) allItems
            else allItems.filter { it.name.contains(trimmed, ignoreCase = true) }
        )
    }

    private fun toggleSelection(item: ItemSummary) {
        if (!selectionMode) return
        if (selectedItems.containsKey(item.id)) selectedItems.remove(item.id)
        else selectedItems[item.id] = item
        syncSelection(item.id)
    }

    private fun syncSelection(changedId: UUID? = null) {
        if (_binding == null || !::adapter.isInitialized) return
        adapter.selectionMode = selectionMode
        if (changedId == null) {
            adapter.notifyDataSetChanged()
            return
        }
        val index = adapter.currentList.indexOfFirst { it.id == changedId }
        if (index >= 0) {
            if (selectedItems.containsKey(changedId)) {
                adapter.notifyItemChanged(index, FilesAdapter.SELECTED)
            } else {
                adapter.notifyItemChanged(index, FilesAdapter.UNSELECTED)
            }
        }
    }

    override fun onUploadStarted(itemId: UUID, parentId: UUID) {
        if (parentId != folderId) {
            return
        }
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        if (position < 0) {
            return
        }
        adapter.notifyItemChanged(
            position,
            FilesAdapter.TRANSFER_STARTED
        )
    }

    override fun onUploadFinished(itemId: UUID, parentId: UUID) {
        if (parentId != folderId) {
            return
        }
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        if (position < 0) {
            return
        }
        adapter.notifyItemChanged(
            position,
            FilesAdapter.TRANSFER_FINISHED
        )
    }

    override fun onDownloadStarted(itemId: UUID, parentId: UUID) {
        onUploadStarted(itemId, parentId)
    }

    override fun onDownloadFinished(itemId: UUID, parentId: UUID) {
        onUploadFinished(itemId, parentId)
    }

    override fun onUploadProgress(
        itemId: UUID,
        parentId: UUID,
        name: String,
        bytesWritten: Long,
        totalBytes: Long
    ) {
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        adapter.transfers[position] = TransferState(
            totalBytes = totalBytes,
            transferredBytes = bytesWritten
        )
        adapter.notifyItemChanged(
            position,
            FilesAdapter.PAYLOAD_PROGRESS
        )
    }

    override fun onDownloadProgress(
        itemId: UUID,
        parentId: UUID,
        bytesReceived: Long,
        totalBytes: Long
    ) {
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        adapter.transfers[position] = TransferState(
            totalBytes = totalBytes,
            transferredBytes = bytesReceived
        )
        adapter.notifyItemChanged(
            position,
            FilesAdapter.PAYLOAD_PROGRESS
        )
    }

    private fun loadItems(): List<ItemSummary> {
        val remote = host()?.loadItems(folderId).orEmpty()
        if (!browseFolders) return remote
        return remote.filter { it.type == ItemType.FOLDER && it.id !in hiddenFolderIds }
    }

    companion object {
        private const val ARG_FOLDER_ID = "folder_id"
        private const val ARG_TITLE = "title"
        private const val ARG_BROWSE_FOLDERS = "browse_folders"
        private const val ARG_HIDDEN_IDS = "hidden_ids"

        fun newInstance(folderId: UUID, title: String) = FilesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FOLDER_ID, folderId.toString())
                putString(ARG_TITLE, title)
            }
        }

        fun newPickerInstance(
            folderId: UUID,
            title: String,
            hiddenIds: Collection<UUID>
        ) = FilesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FOLDER_ID, folderId.toString())
                putString(ARG_TITLE, title)
                putBoolean(ARG_BROWSE_FOLDERS, true)
                putStringArrayList(ARG_HIDDEN_IDS, ArrayList(hiddenIds.map(UUID::toString)))
            }
        }
    }
}
