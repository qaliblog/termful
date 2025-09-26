package com.termux.app.tabs.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.termux.R
import com.termux.app.tabs.EditorTabData
import com.termux.app.tabs.SessionTabManager
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset

/**
 * Fragment for the Text Editor tab
 */
class EditorTabFragment : Fragment() {
    
    private var sessionHandle: String? = null
    
    // UI Components
    private var fileNameText: TextView? = null
    private var modifiedIndicator: TextView? = null
    private var undoButton: ImageButton? = null
    private var redoButton: ImageButton? = null
    private var saveButton: ImageButton? = null
    private var editorMenuButton: ImageButton? = null
    private var editorStatusBar: LinearLayout? = null
    private var cursorPositionText: TextView? = null
    private var fileEncodingText: TextView? = null
    private var languageText: TextView? = null
    private var codeEditor: CodeEditor? = null
    private var noFileState: LinearLayout? = null
    private var externalChangeNotification: MaterialCardView? = null
    private var reloadFileButton: MaterialButton? = null
    private var findReplaceBar: LinearLayout? = null
    private var findEditText: EditText? = null
    private var replaceRow: LinearLayout? = null
    private var replaceEditText: EditText? = null
    
    // File watching
    private var lastFileModified: Long = 0
    
    companion object {
        private const val ARG_SESSION_HANDLE = "session_handle"
        
        fun newInstance(sessionHandle: String): EditorTabFragment {
            return EditorTabFragment().apply {
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
        return inflater.inflate(R.layout.fragment_editor, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupEditorData()
        setupUI()
        checkForFileToOpen()
    }
    
    private fun initViews(view: View) {
        fileNameText = view.findViewById(R.id.file_name_text)
        modifiedIndicator = view.findViewById(R.id.modified_indicator)
        undoButton = view.findViewById(R.id.undo_button)
        redoButton = view.findViewById(R.id.redo_button)
        saveButton = view.findViewById(R.id.save_button)
        editorMenuButton = view.findViewById(R.id.editor_menu_button)
        editorStatusBar = view.findViewById(R.id.editor_status_bar)
        cursorPositionText = view.findViewById(R.id.cursor_position_text)
        fileEncodingText = view.findViewById(R.id.file_encoding_text)
        languageText = view.findViewById(R.id.language_text)
        codeEditor = view.findViewById(R.id.code_editor)
        noFileState = view.findViewById(R.id.no_file_state)
        externalChangeNotification = view.findViewById(R.id.external_change_notification)
        reloadFileButton = view.findViewById(R.id.reload_file_button)
        findReplaceBar = view.findViewById(R.id.find_replace_bar)
        findEditText = view.findViewById(R.id.find_edit_text)
        replaceRow = view.findViewById(R.id.replace_row)
        replaceEditText = view.findViewById(R.id.replace_edit_text)
    }
    
    private fun setupEditorData() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            if (tabData?.editorTabData == null) {
                tabData?.editorTabData = EditorTabData(handle)
            }
        }
    }
    
    private fun setupUI() {
        // Setup button listeners
        undoButton?.setOnClickListener { undo() }
        redoButton?.setOnClickListener { redo() }
        saveButton?.setOnClickListener { saveFile() }
        editorMenuButton?.setOnClickListener { showEditorMenu() }
        reloadFileButton?.setOnClickListener { reloadFile() }
        
        // Setup find/replace
        view?.findViewById<ImageButton>(R.id.find_previous_button)?.setOnClickListener { findPrevious() }
        view?.findViewById<ImageButton>(R.id.find_next_button)?.setOnClickListener { findNext() }
        view?.findViewById<ImageButton>(R.id.close_find_button)?.setOnClickListener { closeFindReplace() }
        view?.findViewById<MaterialButton>(R.id.replace_button)?.setOnClickListener { replaceNext() }
        view?.findViewById<MaterialButton>(R.id.replace_all_button)?.setOnClickListener { replaceAll() }
        
        // Setup code editor
        codeEditor?.apply {
            // Configure editor settings
            isEditable = true
            setTextSize(14f)
            
            // Add text change listener
            subscribeEvent { event ->
                when (event) {
                    is io.github.rosemoe.sora.event.ContentChangeEvent -> {
                        updateModifiedState(true)
                        updateUndoRedoButtons()
                    }
                }
            }
            
            // Add cursor position listener
            // TODO: Implement cursor position tracking
        }
        
        // Setup find text watcher
        findEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performFind(s?.toString() ?: "")
            }
        })
    }
    
    private fun checkForFileToOpen() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData
            
            editorData?.currentFile?.let { filePath ->
                openFile(filePath)
            } ?: run {
                showNoFileState()
            }
        }
    }
    
    private fun openFile(filePath: String) {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (file.exists() && file.isFile) {
                        lastFileModified = file.lastModified()
                        
                        // Detect encoding (simplified)
                        val bytes = file.readBytes()
                        val encoding = detectEncoding(bytes)
                        
                        // Read file content
                        FileInputStream(file).use { fis ->
                            fis.readBytes().toString(Charset.forName(encoding))
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            withContext(Dispatchers.Main) {
                if (content != null) {
                    showEditor()
                    codeEditor?.setText(content)
                    updateFileInfo(filePath)
                    updateModifiedState(false)
                    startFileWatching(filePath)
                } else {
                    showFileError("Failed to open file: $filePath")
                }
            }
        }
    }
    
    private fun detectEncoding(bytes: ByteArray): String {
        // Simple encoding detection - UTF-8 vs Latin-1
        return try {
            String(bytes, Charsets.UTF_8)
            "UTF-8"
        } catch (e: Exception) {
            "ISO-8859-1"
        }
    }
    
    private fun showEditor() {
        noFileState?.visibility = View.GONE
        codeEditor?.visibility = View.VISIBLE
        editorStatusBar?.visibility = View.VISIBLE
    }
    
    private fun showNoFileState() {
        noFileState?.visibility = View.VISIBLE
        codeEditor?.visibility = View.GONE
        editorStatusBar?.visibility = View.GONE
        fileNameText?.text = getString(R.string.no_file_open)
    }
    
    private fun showFileError(message: String) {
        showNoFileState()
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    
    private fun updateFileInfo(filePath: String) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData ?: return
            
            val fileName = File(filePath).name
            fileNameText?.text = fileName
            
            // Update encoding and language
            fileEncodingText?.text = editorData.encoding
            languageText?.text = detectLanguage(fileName)
            
            editorData.currentFile = filePath
        }
    }
    
    private fun detectLanguage(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "py" -> "Python"
            "cpp", "cc", "cxx" -> "C++"
            "c" -> "C"
            "h", "hpp" -> "C/C++ Header"
            "xml" -> "XML"
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            "json" -> "JSON"
            "md" -> "Markdown"
            "sh" -> "Shell Script"
            "yml", "yaml" -> "YAML"
            "gradle" -> "Gradle"
            else -> "Plain Text"
        }
    }
    
    private fun updateModifiedState(isModified: Boolean) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData ?: return
            
            editorData.isModified = isModified
            modifiedIndicator?.visibility = if (isModified) View.VISIBLE else View.GONE
            saveButton?.isEnabled = isModified
        }
    }
    
    private fun updateUndoRedoButtons() {
        codeEditor?.let { editor ->
            undoButton?.isEnabled = editor.canUndo()
            redoButton?.isEnabled = editor.canRedo()
        }
    }
    
    private fun startFileWatching(filePath: String) {
        // Simple file watching - check modification time periodically
        lifecycleScope.launch {
            while (isAdded) {
                kotlinx.coroutines.delay(2000) // Check every 2 seconds
                
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(filePath)
                        val currentModified = file.lastModified()
                        
                        if (currentModified > lastFileModified) {
                            withContext(Dispatchers.Main) {
                                showExternalChangeNotification()
                            }
                        }
                    } catch (e: Exception) {
                        // File might have been deleted
                    }
                }
            }
        }
    }
    
    private fun showExternalChangeNotification() {
        externalChangeNotification?.visibility = View.VISIBLE
    }
    
    private fun reloadFile() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData ?: return
            
            editorData.currentFile?.let { filePath ->
                openFile(filePath)
                externalChangeNotification?.visibility = View.GONE
            }
        }
    }
    
    private fun saveFile() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData ?: return
            
            val filePath = editorData.currentFile ?: return
            val content = codeEditor?.text?.toString() ?: return
            
            lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val file = File(filePath)
                        FileOutputStream(file).use { fos ->
                            fos.write(content.toByteArray(Charset.forName(editorData.encoding)))
                        }
                        
                        lastFileModified = file.lastModified()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        updateModifiedState(false)
                        editorData.lastSavedTime = System.currentTimeMillis()
                        externalChangeNotification?.visibility = View.GONE
                        
                        Toast.makeText(requireContext(), "File saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun undo() {
        codeEditor?.undo()
        updateUndoRedoButtons()
    }
    
    private fun redo() {
        codeEditor?.redo()
        updateUndoRedoButtons()
    }
    
    private fun showEditorMenu() {
        val menuItems = arrayOf(
            "Find & Replace",
            "Go to Line",
            "File Info",
            "Settings"
        )
        
        AlertDialog.Builder(requireContext())
            .setTitle("Editor")
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> showFindReplace()
                    1 -> showGoToLineDialog()
                    2 -> showFileInfo()
                    3 -> showEditorSettings()
                }
            }
            .show()
    }
    
    private fun showFindReplace() {
        findReplaceBar?.visibility = View.VISIBLE
        findEditText?.requestFocus()
    }
    
    private fun closeFindReplace() {
        findReplaceBar?.visibility = View.GONE
        replaceRow?.visibility = View.GONE
        findEditText?.text?.clear()
        replaceEditText?.text?.clear()
    }
    
    private fun performFind(query: String) {
        if (query.isNotEmpty()) {
            // TODO: Implement find functionality with CodeEditor
            codeEditor?.searcher?.search(query)
        }
    }
    
    private fun findNext() {
        // TODO: Implement find next
        codeEditor?.searcher?.gotoNext()
    }
    
    private fun findPrevious() {
        // TODO: Implement find previous  
        codeEditor?.searcher?.gotoPrevious()
    }
    
    private fun replaceNext() {
        replaceRow?.visibility = View.VISIBLE
        // TODO: Implement replace functionality
    }
    
    private fun replaceAll() {
        replaceRow?.visibility = View.VISIBLE
        // TODO: Implement replace all functionality
    }
    
    private fun showGoToLineDialog() {
        val editText = EditText(requireContext())
        editText.hint = "Line number"
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        
        AlertDialog.Builder(requireContext())
            .setTitle("Go to Line")
            .setView(editText)
            .setPositiveButton("Go") { _, _ ->
                val lineText = editText.text.toString()
                if (lineText.isNotEmpty()) {
                    val lineNumber = lineText.toIntOrNull()
                    if (lineNumber != null && lineNumber > 0) {
                        goToLine(lineNumber)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun goToLine(lineNumber: Int) {
        // TODO: Implement go to line functionality
        codeEditor?.setSelection(lineNumber - 1, 0)
    }
    
    private fun showFileInfo() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData ?: return
            
            val filePath = editorData.currentFile ?: return
            val file = File(filePath)
            
            val info = buildString {
                appendLine("Path: $filePath")
                appendLine("Size: ${file.length()} bytes")
                appendLine("Modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(file.lastModified()))}")
                appendLine("Encoding: ${editorData.encoding}")
                appendLine("Language: ${editorData.language}")
                appendLine("Lines: ${codeEditor?.text?.lines()?.size ?: 0}")
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle("File Information")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun showEditorSettings() {
        // TODO: Implement editor settings dialog
        Toast.makeText(requireContext(), "Editor settings coming soon", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPause() {
        super.onPause()
        // Auto-save if there are unsaved changes
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val editorData = tabData?.editorTabData
            
            if (editorData?.isModified == true && editorData.currentFile != null) {
                saveFile()
            }
        }
    }
}