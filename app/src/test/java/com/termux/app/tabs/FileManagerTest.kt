package com.termux.app.tabs

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for File Manager functionality
 */
class FileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fileManagerData: FileManagerTabData
    private lateinit var testDirectory: File

    @Before
    fun setup() {
        testDirectory = tempFolder.root
        fileManagerData = FileManagerTabData(
            sessionHandle = "test-session",
            currentDirectory = testDirectory.absolutePath
        )
    }

    @Test
    fun testFileManagerDataInitialization() {
        assertEquals("Session handle should match", "test-session", fileManagerData.sessionHandle)
        assertEquals("Current directory should match", testDirectory.absolutePath, fileManagerData.currentDirectory)
        assertTrue("Selected files should be empty", fileManagerData.selectedFiles.isEmpty())
        assertTrue("Clipboard files should be empty", fileManagerData.clipboardFiles.isEmpty())
        assertNull("Clipboard operation should be null", fileManagerData.clipboardOperation)
        assertEquals("Sort mode should be NAME_ASC", FileSortMode.NAME_ASC, fileManagerData.sortMode)
        assertEquals("Search query should be empty", "", fileManagerData.searchQuery)
    }

    @Test
    fun testFileSelection() {
        // Create test files
        val file1 = File(testDirectory, "file1.txt")
        val file2 = File(testDirectory, "file2.txt")
        file1.createNewFile()
        file2.createNewFile()

        // Test file selection
        fileManagerData.selectedFiles.add(file1.absolutePath)
        assertEquals("Should have 1 selected file", 1, fileManagerData.selectedFiles.size)
        assertTrue("Should contain file1", fileManagerData.selectedFiles.contains(file1.absolutePath))

        // Test multiple selection
        fileManagerData.selectedFiles.add(file2.absolutePath)
        assertEquals("Should have 2 selected files", 2, fileManagerData.selectedFiles.size)
        assertTrue("Should contain file2", fileManagerData.selectedFiles.contains(file2.absolutePath))

        // Test deselection
        fileManagerData.selectedFiles.remove(file1.absolutePath)
        assertEquals("Should have 1 selected file", 1, fileManagerData.selectedFiles.size)
        assertFalse("Should not contain file1", fileManagerData.selectedFiles.contains(file1.absolutePath))
        assertTrue("Should still contain file2", fileManagerData.selectedFiles.contains(file2.absolutePath))
    }

    @Test
    fun testClipboardOperations() {
        // Create test files
        val file1 = File(testDirectory, "file1.txt")
        val file2 = File(testDirectory, "file2.txt")
        file1.createNewFile()
        file2.createNewFile()

        // Test copy operation
        fileManagerData.selectedFiles.add(file1.absolutePath)
        fileManagerData.selectedFiles.add(file2.absolutePath)

        // Simulate copy operation
        fileManagerData.clipboardFiles.addAll(fileManagerData.selectedFiles)
        fileManagerData.clipboardOperation = ClipboardOperation.COPY
        fileManagerData.selectedFiles.clear()

        assertEquals("Clipboard should have 2 files", 2, fileManagerData.clipboardFiles.size)
        assertEquals("Operation should be COPY", ClipboardOperation.COPY, fileManagerData.clipboardOperation)
        assertTrue("Clipboard should contain file1", fileManagerData.clipboardFiles.contains(file1.absolutePath))
        assertTrue("Clipboard should contain file2", fileManagerData.clipboardFiles.contains(file2.absolutePath))
        assertTrue("Selected files should be empty", fileManagerData.selectedFiles.isEmpty())

        // Test cut operation
        fileManagerData.selectedFiles.add(file1.absolutePath)
        fileManagerData.clipboardFiles.clear()
        fileManagerData.clipboardFiles.addAll(fileManagerData.selectedFiles)
        fileManagerData.clipboardOperation = ClipboardOperation.CUT
        fileManagerData.selectedFiles.clear()

        assertEquals("Clipboard should have 1 file", 1, fileManagerData.clipboardFiles.size)
        assertEquals("Operation should be CUT", ClipboardOperation.CUT, fileManagerData.clipboardOperation)
        assertTrue("Clipboard should contain file1", fileManagerData.clipboardFiles.contains(file1.absolutePath))
    }

    @Test
    fun testSortModes() {
        // Test all sort modes
        val sortModes = FileSortMode.values()
        
        for (sortMode in sortModes) {
            fileManagerData.sortMode = sortMode
            assertEquals("Sort mode should be set correctly", sortMode, fileManagerData.sortMode)
        }

        // Test sort mode display names
        assertEquals("Name A-Z", FileSortMode.NAME_ASC.displayName)
        assertEquals("Name Z-A", FileSortMode.NAME_DESC.displayName)
        assertEquals("Size smallest first", FileSortMode.SIZE_ASC.displayName)
        assertEquals("Size largest first", FileSortMode.SIZE_DESC.displayName)
        assertEquals("Date oldest first", FileSortMode.DATE_ASC.displayName)
        assertEquals("Date newest first", FileSortMode.DATE_DESC.displayName)
        assertEquals("Type A-Z", FileSortMode.TYPE_ASC.displayName)
    }

    @Test
    fun testSearchFunctionality() {
        val searchQuery = "test search"
        fileManagerData.searchQuery = searchQuery
        
        assertEquals("Search query should be set", searchQuery, fileManagerData.searchQuery)

        // Test clearing search
        fileManagerData.searchQuery = ""
        assertEquals("Search query should be empty", "", fileManagerData.searchQuery)
    }

    @Test
    fun testDirectoryNavigation() {
        val originalDir = fileManagerData.currentDirectory
        
        // Create subdirectory
        val subDir = File(testDirectory, "subdir")
        subDir.mkdir()
        
        // Navigate to subdirectory
        fileManagerData.currentDirectory = subDir.absolutePath
        assertEquals("Should be in subdirectory", subDir.absolutePath, fileManagerData.currentDirectory)
        
        // Navigate back
        fileManagerData.currentDirectory = originalDir
        assertEquals("Should be back in original directory", originalDir, fileManagerData.currentDirectory)
    }

    @Test
    fun testCleanup() {
        // Add some data
        fileManagerData.selectedFiles.add("/test/file1")
        fileManagerData.selectedFiles.add("/test/file2")
        fileManagerData.clipboardFiles.add("/test/file3")
        
        // Verify data exists
        assertFalse("Selected files should not be empty", fileManagerData.selectedFiles.isEmpty())
        assertFalse("Clipboard files should not be empty", fileManagerData.clipboardFiles.isEmpty())
        
        // Cleanup
        fileManagerData.cleanup()
        
        // Verify cleanup
        assertTrue("Selected files should be empty after cleanup", fileManagerData.selectedFiles.isEmpty())
        assertTrue("Clipboard files should be empty after cleanup", fileManagerData.clipboardFiles.isEmpty())
    }

    @Test
    fun testFileOperationsStateManagement() {
        val file1 = "/test/file1.txt"
        val file2 = "/test/file2.txt"
        
        // Test full workflow: select -> copy -> paste -> clear
        
        // 1. Select files
        fileManagerData.selectedFiles.add(file1)
        fileManagerData.selectedFiles.add(file2)
        assertEquals("Should have 2 selected files", 2, fileManagerData.selectedFiles.size)
        
        // 2. Copy operation
        fileManagerData.clipboardFiles.addAll(fileManagerData.selectedFiles)
        fileManagerData.clipboardOperation = ClipboardOperation.COPY
        fileManagerData.selectedFiles.clear()
        
        assertEquals("Clipboard should have 2 files", 2, fileManagerData.clipboardFiles.size)
        assertEquals("Operation should be COPY", ClipboardOperation.COPY, fileManagerData.clipboardOperation)
        assertTrue("Selected files should be empty", fileManagerData.selectedFiles.isEmpty())
        
        // 3. Simulate paste operation (clearing clipboard)
        fileManagerData.clipboardFiles.clear()
        fileManagerData.clipboardOperation = null
        
        assertTrue("Clipboard should be empty after paste", fileManagerData.clipboardFiles.isEmpty())
        assertNull("Clipboard operation should be null", fileManagerData.clipboardOperation)
    }

    @Test
    fun testMultipleSelectionToggle() {
        val file1 = "/test/file1.txt"
        val file2 = "/test/file2.txt"
        
        // Test selection toggle
        fileManagerData.selectedFiles.add(file1)
        assertTrue("File1 should be selected", fileManagerData.selectedFiles.contains(file1))
        
        // Toggle (deselect)
        fileManagerData.selectedFiles.remove(file1)
        assertFalse("File1 should not be selected", fileManagerData.selectedFiles.contains(file1))
        
        // Test multiple selection
        fileManagerData.selectedFiles.add(file1)
        fileManagerData.selectedFiles.add(file2)
        assertEquals("Should have 2 selected files", 2, fileManagerData.selectedFiles.size)
        
        // Partial deselection
        fileManagerData.selectedFiles.remove(file1)
        assertEquals("Should have 1 selected file", 1, fileManagerData.selectedFiles.size)
        assertTrue("File2 should still be selected", fileManagerData.selectedFiles.contains(file2))
        
        // Clear all selections
        fileManagerData.selectedFiles.clear()
        assertTrue("No files should be selected", fileManagerData.selectedFiles.isEmpty())
    }
}