package com.termful.app.tabs.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.termful.R
import com.termful.app.tabs.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for the File Manager tab
 */
class FileManagerTabFragment : Fragment() {
    
    private var sessionHandle: String? = null
    
    // UI Components
    private var homeButton: ImageButton? = null
    private var breadcrumbRecycler: RecyclerView? = null
    private var searchButton: ImageButton? = null
    private var sortButton: ImageButton? = null
    private var searchBar: LinearLayout? = null
    private var searchEditText: EditText? = null
    private var closeSearchButton: ImageButton? = null
    private var filesRecyclerView: RecyclerView? = null
    private var emptyState: LinearLayout? = null
    private var loadingProgress: ProgressBar? = null
    private var actionPanel: LinearLayout? = null
    private var selectedCountText: TextView? = null
    private var fabNew: FloatingActionButton? = null
    
    // Adapters
    private var breadcrumbAdapter: BreadcrumbAdapter? = null
    private var filesAdapter: FilesAdapter? = null
    
    companion object {
        private const val ARG_SESSION_HANDLE = "session_handle"
        
        fun newInstance(sessionHandle: String): FileManagerTabFragment {
            return FileManagerTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SESSION_HANDLE, sessionHandle)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionHandle = arguments?.getString(ARG_SESSION_HANDLE)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_file_manager, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupFileManagerData()
        setupUI()
        loadCurrentDirectory()
    }
    
    private fun initViews(view: View) {
        homeButton = view.findViewById(R.id.home_button)
        breadcrumbRecycler = view.findViewById(R.id.breadcrumb_recycler)
        searchButton = view.findViewById(R.id.search_button)
        sortButton = view.findViewById(R.id.sort_button)
        searchBar = view.findViewById(R.id.search_bar)
        searchEditText = view.findViewById(R.id.search_edit_text)
        closeSearchButton = view.findViewById(R.id.close_search_button)
        filesRecyclerView = view.findViewById(R.id.files_recycler_view)
        emptyState = view.findViewById(R.id.empty_state)
        loadingProgress = view.findViewById(R.id.loading_progress)
        actionPanel = view.findViewById(R.id.action_panel)
        selectedCountText = view.findViewById(R.id.selected_count_text)
        fabNew = view.findViewById(R.id.fab_new)
    }
    
    private fun setupFileManagerData() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            if (tabData?.fileManagerTabData == null) {
                tabData?.fileManagerTabData = FileManagerTabData(handle)
            }
        }
    }
    
    private fun setupUI() {
        // Setup RecyclerViews
        breadcrumbAdapter = BreadcrumbAdapter { path -> navigateToPath(path) }
        breadcrumbRecycler?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = breadcrumbAdapter
        }
        
        filesAdapter = FilesAdapter(
            onFileClick = { file -> onFileClick(file) },
            onFileLongClick = { file -> onFileLongClick(file) },
            onSelectionChanged = { count -> updateSelectionUI(count) }
        )
        filesRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = filesAdapter
        }
        
        // Setup button listeners
        homeButton?.setOnClickListener { navigateToHome() }
        searchButton?.setOnClickListener { toggleSearch() }
        sortButton?.setOnClickListener { showSortDialog() }
        closeSearchButton?.setOnClickListener { closeSearch() }
        fabNew?.setOnClickListener { showNewFileDialog() }
        
        // Setup action panel
        view?.findViewById<ImageButton>(R.id.action_copy)?.setOnClickListener { copySelected() }
        view?.findViewById<ImageButton>(R.id.action_cut)?.setOnClickListener { cutSelected() }
        view?.findViewById<ImageButton>(R.id.action_delete)?.setOnClickListener { deleteSelected() }
        view?.findViewById<ImageButton>(R.id.action_more)?.setOnClickListener { showMoreActions() }
    }
    
    private fun loadCurrentDirectory() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            lifecycleScope.launch {
                loadingProgress?.visibility = View.VISIBLE
                emptyState?.visibility = View.GONE
                
                val files = withContext(Dispatchers.IO) {
                    loadFiles(fileManagerData.currentDirectory, fileManagerData.sortMode)
                }
                
                withContext(Dispatchers.Main) {
                    loadingProgress?.visibility = View.GONE
                    updateBreadcrumb(fileManagerData.currentDirectory)
                    filesAdapter?.updateFiles(files)
                    
                    if (files.isEmpty()) {
                        emptyState?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
    
    private suspend fun loadFiles(directory: String, sortMode: FileSortMode): List<FileItem> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(directory)
                if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
                
                val files = dir.listFiles()?.map { file ->
                    FileItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0,
                        lastModified = file.lastModified(),
                        isHidden = file.isHidden,
                        permissions = getPermissions(file)
                    )
                } ?: emptyList()
                
                // Sort files
                when (sortMode) {
                    FileSortMode.NAME_ASC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    FileSortMode.NAME_DESC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
                    FileSortMode.SIZE_ASC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.size })
                    FileSortMode.SIZE_DESC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
                    FileSortMode.DATE_ASC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified })
                    FileSortMode.DATE_DESC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
                    FileSortMode.TYPE_ASC -> files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { getFileExtension(it.name) })
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    private fun getPermissions(file: File): String {
        val perms = StringBuilder()
        perms.append(if (file.canRead()) "r" else "-")
        perms.append(if (file.canWrite()) "w" else "-")
        perms.append(if (file.canExecute()) "x" else "-")
        return perms.toString()
    }
    
    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(lastDot + 1).lowercase() else ""
    }
    
    private fun updateBreadcrumb(path: String) {
        val segments = path.split("/").filter { it.isNotEmpty() }
        val breadcrumbItems = mutableListOf<BreadcrumbItem>()
        
        // Add root
        breadcrumbItems.add(BreadcrumbItem("/", "/"))
        
        // Add path segments
        var currentPath = ""
        for (segment in segments) {
            currentPath += "/$segment"
            breadcrumbItems.add(BreadcrumbItem(segment, currentPath))
        }
        
        breadcrumbAdapter?.updateItems(breadcrumbItems)
    }
    
    private fun navigateToPath(path: String) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            fileManagerData.currentDirectory = path
            fileManagerData.selectedFiles.clear()
            updateSelectionUI(0)
            loadCurrentDirectory()
        }
    }
    
    private fun navigateToHome() {
        val homeDir = System.getProperty("user.home") ?: "/"
        navigateToPath(homeDir)
    }
    
    private fun onFileClick(file: FileItem) {
        if (file.isDirectory) {
            navigateToPath(file.path)
        } else {
            // Open file in editor
            openFileInEditor(file.path)
        }
    }
    
    private fun onFileLongClick(file: FileItem) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            if (fileManagerData.selectedFiles.contains(file.path)) {
                fileManagerData.selectedFiles.remove(file.path)
            } else {
                fileManagerData.selectedFiles.add(file.path)
            }
            
            filesAdapter?.updateSelection(fileManagerData.selectedFiles)
            updateSelectionUI(fileManagerData.selectedFiles.size)
        }
    }
    
    private fun openFileInEditor(filePath: String) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            
            // Initialize editor tab data if needed
            if (tabData?.editorTabData == null) {
                tabData?.editorTabData = EditorTabData(handle)
            }
            
            // Set the file to open
            tabData?.editorTabData?.currentFile = filePath
            
            // Switch to editor tab
            tabData?.setCurrentTab(TabType.EDITOR)
        }
    }
    
    private fun updateSelectionUI(count: Int) {
        if (count > 0) {
            actionPanel?.visibility = View.VISIBLE
            selectedCountText?.text = "$count selected"
        } else {
            actionPanel?.visibility = View.GONE
        }
    }
    
    private fun toggleSearch() {
        searchBar?.visibility = if (searchBar?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (searchBar?.visibility == View.VISIBLE) {
            searchEditText?.requestFocus()
        }
    }
    
    private fun closeSearch() {
        searchBar?.visibility = View.GONE
        searchEditText?.text?.clear()
        // TODO: Clear search filter
    }
    
    private fun showSortDialog() {
        val sortOptions = FileSortMode.values().map { it.displayName }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->
                sessionHandle?.let { handle ->
                    val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
                    val fileManagerData = tabData?.fileManagerTabData ?: return@setItems
                    
                    fileManagerData.sortMode = FileSortMode.values()[which]
                    loadCurrentDirectory()
                }
            }
            .show()
    }
    
    private fun showNewFileDialog() {
        val options = arrayOf("New File", "New Folder")
        
        AlertDialog.Builder(requireContext())
            .setTitle("Create")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateFileDialog()
                    1 -> showCreateFolderDialog()
                }
            }
            .show()
    }
    
    private fun showCreateFileDialog() {
        val editText = EditText(requireContext())
        editText.hint = "File name"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Create File")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    createFile(fileName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCreateFolderDialog() {
        val editText = EditText(requireContext())
        editText.hint = "Folder name"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Create Folder")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    createFolder(folderName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createFile(fileName: String) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(fileManagerData.currentDirectory, fileName)
                        file.createNewFile()
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
                loadCurrentDirectory()
            }
        }
    }
    
    private fun createFolder(folderName: String) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val folder = File(fileManagerData.currentDirectory, folderName)
                        folder.mkdirs()
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
                loadCurrentDirectory()
            }
        }
    }
    
    private fun copySelected() {
        performClipboardOperation(ClipboardOperation.COPY)
    }
    
    private fun cutSelected() {
        performClipboardOperation(ClipboardOperation.CUT)
    }
    
    private fun performClipboardOperation(operation: ClipboardOperation) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            fileManagerData.clipboardFiles.clear()
            fileManagerData.clipboardFiles.addAll(fileManagerData.selectedFiles)
            fileManagerData.clipboardOperation = operation
            
            fileManagerData.selectedFiles.clear()
            updateSelectionUI(0)
            filesAdapter?.updateSelection(emptySet())
            
            Toast.makeText(
                requireContext(),
                "${if (operation == ClipboardOperation.COPY) "Copied" else "Cut"} ${fileManagerData.clipboardFiles.size} items",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun deleteSelected() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val fileManagerData = tabData?.fileManagerTabData ?: return
            
            val selectedFiles = fileManagerData.selectedFiles.toList()
            
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ${selectedFiles.size} items?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteFiles(selectedFiles)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun deleteFiles(filePaths: List<String>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                filePaths.forEach { path ->
                    try {
                        val file = File(path)
                        file.deleteRecursively()
                    } catch (e: Exception) {
                        // Handle individual file deletion errors
                    }
                }
            }
            
            sessionHandle?.let { handle ->
                val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
                val fileManagerData = tabData?.fileManagerTabData
                fileManagerData?.selectedFiles?.clear()
                updateSelectionUI(0)
            }
            
            loadCurrentDirectory()
        }
    }
    
    private fun showMoreActions() {
        // TODO: Implement more actions (rename, properties, etc.)
    }
}

// Data classes for file manager
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    val permissions: String
)

data class BreadcrumbItem(
    val name: String,
    val path: String
)

// Adapter classes would need to be implemented
class FilesAdapter(
    private val onFileClick: (FileItem) -> Unit,
    private val onFileLongClick: (FileItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var files = listOf<FileItem>()
    private var selectedFiles = setOf<String>()
    
    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        notifyDataSetChanged()
    }
    
    fun updateSelection(selection: Set<String>) {
        selectedFiles = selection
        notifyDataSetChanged()
    }
    
    override fun getItemCount() = files.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // TODO: Implement ViewHolder
        return object : RecyclerView.ViewHolder(View(parent.context)) {}
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // TODO: Implement bind
    }
}

class BreadcrumbAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items = listOf<BreadcrumbItem>()
    
    fun updateItems(newItems: List<BreadcrumbItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun getItemCount() = items.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // TODO: Implement ViewHolder
        return object : RecyclerView.ViewHolder(View(parent.context)) {}
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // TODO: Implement bind
    }
}